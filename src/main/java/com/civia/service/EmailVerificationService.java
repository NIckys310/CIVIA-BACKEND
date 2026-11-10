package com.civia.service;

import com.civia.domain.User;
import com.civia.domain.UserToken;
import com.civia.repo.UserRepository;
import com.civia.repo.UserTokenRepository;
import com.civia.security.Hashing;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Confirma que quien se registra controla su correo. El enlace es de un solo uso y vence en 24 horas. */
@Service
public class EmailVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);
    static final Duration TTL = Duration.ofHours(24);
    static final int MAX_PER_HOUR = 3;

    private final UserRepository users;
    private final UserTokenRepository userTokens;
    private final MailService mail;
    private final TaskExecutor executor;
    private final TransactionTemplate tx;
    private final String publicUrl;

    public EmailVerificationService(UserRepository users, UserTokenRepository userTokens, MailService mail,
                                    @Qualifier("applicationTaskExecutor") TaskExecutor executor, TransactionTemplate tx,
                                    @Value("${civia.mail.public-url:http://localhost:8080}") String publicUrl) {
        this.users = users;
        this.userTokens = userTokens;
        this.mail = mail;
        this.executor = executor;
        this.tx = tx;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    /** Envía el enlace en segundo plano; sin un canal de correo real no hace nada. */
    public void send(UUID userId) {
        if (!mail.deliverable()) return;
        executor.execute(() -> {
            try {
                issue(userId).ifPresent(mail::send);
            } catch (Exception e) {
                LOG.error("No se pudo enviar la verificación de correo", e);
            }
        });
    }

    private Optional<MailService.Mail> issue(UUID userId) {
        return tx.execute(status -> {
            User user = users.findById(userId).orElse(null);
            if (user == null || user.getEmailVerifiedAt() != null) return Optional.<MailService.Mail>empty();
            Instant now = Instant.now();
            if (userTokens.countByUserIdAndPurposeAndCreatedAtAfter(userId, UserToken.EMAIL_VERIFY, now.minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
                return Optional.<MailService.Mail>empty();
            }
            userTokens.invalidateAll(userId, UserToken.EMAIL_VERIFY, now);
            String raw = Hashing.randomToken();
            UserToken t = new UserToken();
            t.setUser(user);
            t.setPurpose(UserToken.EMAIL_VERIFY);
            t.setTokenHash(Hashing.sha256(raw));
            t.setExpiresAt(now.plus(TTL));
            userTokens.save(t);
            return Optional.of(new MailService.Mail(user.getEmail(), "Confirma tu correo en CIVIA AI", """
                    Hola, %s:

                    Confirma tu correo para activar todas las funciones de CIVIA AI:

                    %s/#/verify?token=%s

                    El enlace vence en 24 horas. Si no creaste esta cuenta, ignora este mensaje.

                    — CIVIA Auth
                    """.formatted(user.getFullName(), publicUrl, raw)));
        });
    }

    @Transactional
    public void verify(String rawToken) {
        ResponseStatusException invalid = new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace no es válido o ya venció.");
        if (rawToken == null || rawToken.isBlank()) throw invalid;
        Instant now = Instant.now();
        UserToken t = userTokens.findByTokenHashAndPurpose(Hashing.sha256(rawToken.strip()), UserToken.EMAIL_VERIFY)
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(now))
                .orElseThrow(() -> invalid);
        if (userTokens.consume(t.getId(), now) != 1) throw invalid;
        t.getUser().setEmailVerifiedAt(now);
    }
}
