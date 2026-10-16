package com.civia.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Claves de la aplicación: firma de JWT (HMAC-SHA256) y cifrado de datos en reposo (AES-256).
 *
 * <p>En producción se pasan por variables de entorno ({@code CIVIA_JWT_SECRET}, {@code CIVIA_DATA_KEY},
 * en Base64 de 32 bytes o más). En desarrollo se generan una vez y se guardan en {@code data/keys}
 * para que las sesiones y los datos cifrados sobrevivan a los reinicios.
 */
@Component
public class KeyMaterial {

    private static final Logger LOG = LoggerFactory.getLogger(KeyMaterial.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey jwtKey;
    private final SecretKey dataKey;

    public KeyMaterial(@Value("${civia.security.jwt-secret:}") String jwtSecret,
                       @Value("${civia.security.data-key:}") String dataKey,
                       @Value("${civia.security.keys-dir:./data/keys}") String keysDir) {
        Path dir = Path.of(keysDir);
        this.jwtKey = new SecretKeySpec(resolve(jwtSecret, dir.resolve("jwt.key"), 64), "HmacSHA256");
        this.dataKey = new SecretKeySpec(resolve(dataKey, dir.resolve("data.key"), 32), "AES");
    }

    public SecretKey jwtKey() {
        return jwtKey;
    }

    public SecretKey dataKey() {
        return dataKey;
    }

    private static byte[] resolve(String configured, Path file, int size) {
        if (configured != null && !configured.isBlank()) {
            byte[] key = Base64.getDecoder().decode(configured.strip());
            if (key.length < 32) throw new IllegalStateException("Las claves deben tener al menos 32 bytes (Base64).");
            return size == 32 ? java.util.Arrays.copyOf(key, 32) : key;
        }
        try {
            if (Files.exists(file)) {
                return Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).strip());
            }
            byte[] key = new byte[size];
            RANDOM.nextBytes(key);
            Files.createDirectories(file.getParent());
            Files.writeString(file, Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII);
            restrict(file);
            LOG.warn("Se generó la clave {}. En producción define la variable de entorno correspondiente.", file);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.copyOf(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: el archivo hereda los permisos de la carpeta del usuario.
        }
    }
}
