package com.civia.security;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Canal sellado de la API: cifra de extremo a extremo (navegador ↔ servidor) los cuerpos JSON, por
 * encima de TLS. Así el correo, la contraseña y los datos de las respuestas no aparecen en claro en las
 * herramientas del navegador, en extensiones que leen peticiones ni en proxies que terminan TLS.
 *
 * <p>Acuerdo de claves ECDH P-256 con claves efímeras de ambos lados, HKDF-SHA256 y AES-256-GCM. El
 * texto asociado (AAD) une cada mensaje a su método, ruta y sentido: un cuerpo cifrado para
 * {@code POST /auth/token} no se puede reutilizar en otra ruta ni como respuesta.
 *
 * <p>Las claves viven solo en memoria, con caducidad y un tope de entradas. Si el servidor se reinicia,
 * el cliente recibe 428 y repite el saludo.
 */
public final class SealedChannel {

    static final String INFO = "civia-seal-v1";
    static final int IV_BYTES = 12;
    static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final long ttlMillis;
    private final int maxEntries;
    private final Map<String, Entry> keys;

    private record Entry(byte[] key, long expiresAt) { }

    /** Resultado del saludo: identificador del canal y clave pública del servidor (punto sin comprimir). */
    public record Handshake(String id, String publicKey) { }

    public SealedChannel(Clock clock, long ttlMillis, int maxEntries) {
        this.clock = clock;
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.keys = new LinkedHashMap<>(256, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > SealedChannel.this.maxEntries;
            }
        };
    }

    public SealedChannel() {
        this(Clock.systemUTC(), 8 * 60 * 60 * 1000L, 50_000);
    }

    /** Recibe la clave pública efímera del navegador y deriva la clave AES compartida. */
    public Handshake open(String clientPublicKeyB64) throws GeneralSecurityException {
        byte[] raw = b64d(clientPublicKeyB64);
        if (raw.length != 65 || raw[0] != 0x04) throw new GeneralSecurityException("Clave pública no válida");

        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"), random);
        KeyPair server = gen.generateKeyPair();
        ECParameterSpec params = ((ECPublicKey) server.getPublic()).getParams();

        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        // generatePublic valida que el punto esté en la curva (evita ataques de curva inválida).
        ECPublicKey client = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, params));

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(server.getPrivate());
        ka.doPhase(client, true);
        byte[] shared = ka.generateSecret();

        byte[] idBytes = new byte[18];
        random.nextBytes(idBytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(idBytes);
        byte[] key = hkdf(shared, id.getBytes(StandardCharsets.UTF_8), INFO.getBytes(StandardCharsets.UTF_8), 32);
        Arrays.fill(shared, (byte) 0);

        long now = clock.millis();
        synchronized (keys) {
            purge(now);
            keys.put(id, new Entry(key, now + ttlMillis));
        }
        return new Handshake(id, b64e(encodePoint((ECPublicKey) server.getPublic())));
    }

    /** Clave del canal, si existe y no ha caducado. */
    Optional<byte[]> key(String id) {
        if (id == null || id.isBlank() || id.length() > 64) return Optional.empty();
        long now = clock.millis();
        synchronized (keys) {
            Entry e = keys.get(id);
            if (e == null) return Optional.empty();
            if (e.expiresAt() < now) { keys.remove(id); return Optional.empty(); }
            return Optional.of(e.key());
        }
    }

    public static byte[] seal(byte[] key, byte[] plain, String aad, SecureRandom random) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] ct = c.doFinal(plain);
        String json = "{\"iv\":\"" + b64e(iv) + "\",\"data\":\"" + b64e(ct) + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] unseal(byte[] key, String ivB64, String dataB64, String aad) throws GeneralSecurityException {
        byte[] iv = b64d(ivB64);
        if (iv.length != IV_BYTES) throw new GeneralSecurityException("IV no válido");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return c.doFinal(b64d(dataB64));
    }

    SecureRandom random() {
        return random;
    }

    private void purge(long now) {
        keys.values().removeIf(e -> e.expiresAt() < now);
    }

    static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[length];
        byte[] t = new byte[0];
        int pos = 0;
        for (int i = 1; pos < length; i++) {
            mac.update(t);
            mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
            int n = Math.min(t.length, length - pos);
            System.arraycopy(t, 0, out, pos, n);
            pos += n;
        }
        return out;
    }

    static byte[] encodePoint(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyFixed(key.getW().getAffineX(), out, 1);
        copyFixed(key.getW().getAffineY(), out, 33);
        return out;
    }

    private static void copyFixed(BigInteger v, byte[] out, int offset) {
        byte[] b = v.toByteArray();
        int start = Math.max(0, b.length - 32);
        int len = b.length - start;
        System.arraycopy(b, start, out, offset + 32 - len, len);
    }

    static String b64e(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    static byte[] b64d(String s) {
        try {
            return Base64.getDecoder().decode(s == null ? "" : s);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
