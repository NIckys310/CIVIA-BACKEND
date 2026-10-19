package com.civia.service;

import jakarta.annotation.PostConstruct;
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

    public MailService(ObjectProvider<JavaMailSender> senders,
                       @Value("${civia.mail.mode:log}") String mode,
                       @Value("${civia.mail.from:CIVIA AI <no-reply@civia.local>}") String from,
                       @Value("${civia.mail.log-links:true}") boolean logLinks) {
        this.senders = senders;
        this.mode = mode;
        this.from = from;
        this.logLinks = logLinks;
    }

    @PostConstruct
    void check() {
        if ("smtp".equals(mode) && senders.getIfAvailable() == null) {
            throw new IllegalStateException("civia.mail.mode=smtp requiere spring.mail.host (SPRING_MAIL_HOST).");
        }
        if (!"smtp".equals(mode)) {
            LOG.warn("Correo en modo log: los mensajes se escriben en el log y no se envían. Configura CIVIA_MAIL_MODE=smtp en producción.");
        }
    }

    public void send(Mail mail) {
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
