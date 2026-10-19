package com.civia.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** TOTP (RFC 6238, SHA-1, 6 dígitos, 30 s) compatible con Google Authenticator, Authy, 1Password… */
public final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static String otpauthUri(String secret, String account) {
        String label = java.net.URLEncoder.encode("CIVIA AI:" + account, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=CIVIA%20AI&digits=6&period=30";
    }

    /** Acepta el código actual y el de la ventana anterior/siguiente (desfase de reloj). */
    public static boolean verify(String secret, String code, Instant now) {
        if (secret == null || code == null) return false;
        String digits = code.replaceAll("\\s", "");
        if (!digits.matches("\\d{6}")) return false;
        long step = now.getEpochSecond() / 30;
        for (long i = -1; i <= 1; i++) {
            if (Hashing.constantTimeEquals(code(secret, step + i), digits)) return true;
        }
        return false;
    }

    static String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0xf;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    private static byte[] unbase32(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Base32 inválido");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
