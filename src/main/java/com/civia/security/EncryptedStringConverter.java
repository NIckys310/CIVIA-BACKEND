package com.civia.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Component;

/**
 * Cifra columnas sensibles (teléfonos, correos de clientes, semillas MFA, matrícula profesional)
 * con AES-256-GCM. Formato guardado: {@code v1:<base64(iv || ciphertext+tag)>}.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeyMaterial keys;

    public EncryptedStringConverter(KeyMaterial keys) {
        this.keys = keys;
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.dataKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.dataKey(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el dato (¿cambió CIVIA_DATA_KEY?)", e);
        }
    }
}
