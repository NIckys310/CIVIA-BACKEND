package com.civia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Correo saliente en texto plano.
 *
 * <ul>
 *   <li>{@code civia.mail.mode=smtp}: envía con SMTP ({@code spring.mail.*}, p. ej. {@code SPRING_MAIL_HOST}).</li>
 *   <li>{@code civia.mail.mode=log} (desarrollo): escribe el correo en el log. Con
 *       {@code civia.mail.log-links=false} (perfil prod) se ocultan los enlaces, que dan acceso a la cuenta.</li>
 * </ul>
 */
@Service
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    public record Mail(String to, String subject, String text) {}

    private final ObjectProvider<JavaMailSender> senders;
    private final String mode;
    private final String from;
    private final boolean logLinks;
    private final String brevoKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public MailService(ObjectProvider<JavaMailSender> senders,
                       @Value("${civia.mail.mode:log}") String mode,
                       @Value("${civia.mail.from:CIVIA AI <no-reply@civia.local>}") String from,
                       @Value("${civia.mail.log-links:true}") boolean logLinks,
                       @Value("${civia.mail.brevo-api-key:}") String brevoKey) {
        this.senders = senders;
        this.mode = mode;
        this.from = from;
        this.logLinks = logLinks;
        this.brevoKey = brevoKey;
    }

    /** true si los correos llegan de verdad a su destinatario (o, en desarrollo, si el enlace queda en el log). */
    public boolean deliverable() {
        return "smtp".equals(mode) || "brevo".equals(mode) || logLinks;
    }

    @PostConstruct
    void check() {
        if ("smtp".equals(mode) && senders.getIfAvailable() == null) {
            throw new IllegalStateException("civia.mail.mode=smtp requiere spring.mail.host (SPRING_MAIL_HOST).");
        }
        if ("brevo".equals(mode) && brevoKey.isBlank()) {
            throw new IllegalStateException("civia.mail.mode=brevo requiere BREVO_API_KEY.");
        }
        if (!"smtp".equals(mode) && !"brevo".equals(mode)) {
            LOG.warn("Correo en modo log: los mensajes se escriben en el log y no se envían. Configura CIVIA_MAIL_MODE=smtp en producción.");
        }
    }

    /** Envío por HTTPS: el plan gratuito de Render bloquea los puertos SMTP. */
    private void sendWithBrevo(Mail mail) {
        var matcher = java.util.regex.Pattern.compile("^(.*?)\\s*<([^>]+)>$").matcher(from.strip());
        String name = matcher.matches() ? matcher.group(1) : "CIVIA AI";
        String address = matcher.matches() ? matcher.group(2) : from.strip();
        try {
            String body = json.writeValueAsString(Map.of(
                    "sender", Map.of("name", name, "email", address),
                    "to", List.of(Map.of("email", mail.to())),
                    "subject", mail.subject(),
                    "textContent", mail.text()));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", brevoKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("El proveedor de correo respondió " + response.statusCode());
            }
            LOG.info("Correo enviado: {}", mail.subject());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envío de correo interrumpido", e);
        }
    }

    public void send(Mail mail) {
        if ("brevo".equals(mode)) {
            sendWithBrevo(mail);
            return;
        }
        if ("smtp".equals(mode)) {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(mail.to());
            m.setSubject(mail.subject());
            m.setText(mail.text());
            senders.getObject().send(m);
            LOG.info("Correo enviado a {}: {}", mail.to(), mail.subject());
            return;
        }
        String text = logLinks ? mail.text() : mail.text().replaceAll("https?://\\S+", "[enlace oculto]");
        LOG.info("""

                ---------- correo (modo log) ----------
                De: {}
                Para: {}
                Asunto: {}

                {}
                ---------------------------------------""", from, mail.to(), mail.subject(), text);
    }
}
