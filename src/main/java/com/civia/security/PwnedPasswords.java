package com.civia.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consulta de contraseñas filtradas en Have I Been Pwned (Pwned Passwords) con k-anonymity:
 * solo salen del servidor los 5 primeros caracteres hexadecimales del SHA-1; la comparación del
 * resto se hace aquí. Se pide relleno ({@code Add-Padding}) para ocultar el tamaño de la respuesta.
 *
 * <p>Fail-open: si el servicio no responde, la contraseña se acepta y se registra la falla.
 */
@Component
public class PwnedPasswords {

    private static final Logger LOG = LoggerFactory.getLogger(PwnedPasswords.class);

    /** Obtiene el cuerpo de {@code /range/{prefijo}}; reemplazable en pruebas. */
    @FunctionalInterface
    interface RangeClient {
        String range(String prefix) throws IOException, InterruptedException;
    }

    private final boolean enabled;
    private final RangeClient client;

    @Autowired
    public PwnedPasswords(@Value("${civia.security.hibp.enabled:true}") boolean enabled,
                          @Value("${civia.security.hibp.url:https://api.pwnedpasswords.com/range/}") String url,
                          @Value("${civia.security.hibp.timeout-ms:3000}") long timeoutMs) {
        this(enabled, http(url, Duration.ofMillis(timeoutMs)));
    }

    PwnedPasswords(boolean enabled, RangeClient client) {
        this.enabled = enabled;
        this.client = client;
    }

    private static RangeClient http(String url, Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        return prefix -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + prefix))
                    .timeout(timeout)
                    .header("Add-Padding", "true")
                    .header("User-Agent", "CIVIA-AI")
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
            return res.body();
        };
    }

    /** Veces que la contraseña aparece en filtraciones (0 si no aparece, está desactivado o falla). */
    public long breachCount(String raw) {
        if (!enabled || raw == null || raw.isEmpty()) return 0;
        String sha1 = sha1(raw);
        String prefix = sha1.substring(0, 5);
        String suffix = sha1.substring(5);
        try {
            for (String line : client.range(prefix).split("\\R")) {
                int colon = line.indexOf(':');
                if (colon == 35 && line.regionMatches(true, 0, suffix, 0, 35)) {
                    // Las entradas de relleno traen conteo 0.
                    return Long.parseLong(line.substring(colon + 1).strip());
                }
            }
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Consulta a Have I Been Pwned interrumpida; se acepta la contraseña.");
            return 0;
        } catch (Exception e) {
            LOG.warn("No se pudo consultar Have I Been Pwned ({}); se acepta la contraseña.", e.toString());
            return 0;
        }
    }

    static String sha1(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
