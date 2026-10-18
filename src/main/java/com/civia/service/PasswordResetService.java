package com.civia.service;

import com.civia.domain.Enums;
import com.civia.domain.User;
import com.civia.domain.UserToken;
import com.civia.repo.UserRepository;
import com.civia.repo.UserTokenRepository;
import com.civia.security.Hashing;
import com.civia.security.PasswordService;
import com.civia.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

/**
 * Recuperación de contraseña por correo.
 *
 * <ul>
 *   <li>La solicitud responde igual exista o no la cuenta y se procesa en segundo plano, para no
 *       revelar por el tiempo de respuesta si el correo está registrado.</li>
 *   <li>El enlace lleva un token aleatorio de 256 bits; en {@code user_tokens} solo queda su SHA-256.
 *       Vence a los 30 minutos, es de un solo uso y pedir otro invalida los anteriores.</li>
 *   <li>Al cambiar la contraseña se cierran todas las sesiones. No se inicia sesión automáticamente:
 *       quien tenga verificación en dos pasos la sigue necesitando.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

    static final Duration TTL = Duration.ofMinutes(30);
    /** Máximo de correos de recuperación por cuenta y hora (evita usar CIVIA para inundar un buzón). */
    static final int MAX_PER_HOUR = 3;

    private final UserRepository users;
    private final UserTokenRepository userTokens;
    private final PasswordService passwords;
    private final TokenService tokens;
    private final AuditService audit;
    private final MailService mail;
    private final TaskExecutor executor;
    private final TransactionTemplate tx;
    private final String publicUrl;

    public PasswordResetService(UserRepository users, UserTokenRepository userTokens, PasswordService passwords,
                                TokenService tokens, AuditService audit, MailService mail,
                                @Qualifier("applicationTaskExecutor") TaskExecutor executor, TransactionTemplate tx,
                                @Value("${civia.mail.public-url:http://localhost:8080}") String publicUrl) {
        this.users = users;
        this.userTokens = userTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.audit = audit;
        this.mail = mail;
        this.executor = executor;
        this.tx = tx;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    /** Agenda el envío del enlace. No devuelve nada: la respuesta al cliente es siempre la misma. */
    public void requestReset(String email, String ip, String userAgent) {
        String normalized = AuthService.normalizeEmail(email);
        if (normalized.isEmpty()) return;
        executor.execute(() -> {
            try {
                issue(normalized, ip, userAgent).ifPresent(mail::send);
            } catch (Exception e) {
                LOG.error("No se pudo procesar la recuperación de contraseña", e);
            }
        });
    }

    private Optional<MailService.Mail> issue(String email, String ip, String userAgent) {
        return tx.execute(status -> {
            User user = users.findByEmail(email).orElse(null);
            if (user == null || user.getStatus() != Enums.UserStatus.ACTIVE) return Optional.<MailService.Mail>empty();
            Instant now = Instant.now();
            if (userTokens.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), UserToken.PASSWORD_RESET, now.minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
                audit.security(user, "password-reset-throttled", ip, userAgent, "Demasiadas solicitudes de recuperación");
                return Optional.<MailService.Mail>empty();
            }
            userTokens.invalidateAll(user.getId(), UserToken.PASSWORD_RESET, now);
            String raw = Hashing.randomToken();
            UserToken t = new UserToken();
            t.setUser(user);
            t.setPurpose(UserToken.PASSWORD_RESET);
            t.setTokenHash(Hashing.sha256(raw));
            t.setExpiresAt(now.plus(TTL));
            t.setIpAddress(ip);
            userTokens.save(t);
            audit.security(user, "password-reset-requested", ip, userAgent, "Se envió un enlace de recuperación");
            String link = publicUrl + "/#/reset?token=" + raw;
            return Optional.of(new MailService.Mail(user.getEmail(), "Restablece tu contraseña de CIVIA AI", """
                    Hola, %s:

                    Recibimos una solicitud para restablecer la contraseña de tu cuenta de CIVIA AI.
                    Abre este enlace para elegir una nueva (vence en %d minutos y solo funciona una vez):

                    %s

                    Al cambiarla se cerrarán todas tus sesiones abiertas.
                    Si no fuiste tú, ignora este correo: tu contraseña actual sigue funcionando.

                    — CIVIA Auth
                    """.formatted(user.getFullName(), TTL.toMinutes(), link)));
        });
    }

    /** Cambia la contraseña con un token válido y cierra todas las sesiones. */
    @Transactional
    public void reset(String rawToken, String newPassword, String ip, String userAgent) {
        ResponseStatusException invalid = new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El enlace no es válido o ya venció. Solicita uno nuevo.");
        if (rawToken == null || rawToken.isBlank()) throw invalid;
        Instant now = Instant.now();
        UserToken t = userTokens.findByTokenHashAndPurpose(Hashing.sha256(rawToken.strip()), UserToken.PASSWORD_RESET)
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(now))
                .orElseThrow(() -> invalid);
        User user = t.getUser();
        if (user.getStatus() != Enums.UserStatus.ACTIVE) throw invalid;

        // Se valida antes de consumir el token: si la contraseña no sirve, el enlace sigue vigente.
        passwords.requireStrong(newPassword, user.getEmail(), user.getFullName());
        passwords.requireNotReused(user, newPassword);
        if (userTokens.consume(t.getId(), now) != 1) throw invalid;
        userTokens.invalidateAll(user.getId(), UserToken.PASSWORD_RESET, now);

        passwords.remember(user);
        user.setPasswordHash(passwords.hash(newPassword));
        user.setPasswordChangedAt(now);
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        users.save(user);
        tokens.revokeAll(user.getId());
        audit.security(user, "password-reset", ip, userAgent, "Contraseña restablecida por correo; se cerraron todas las sesiones");
        audit.notify(user, "security", "Contraseña restablecida",
                "Tu contraseña se cambió con un enlace de recuperación. Si no fuiste tú, contáctanos.", "#/profile");
        String email = user.getEmail();
        String name = user.getFullName();
        executor.execute(() -> {
            try {
                mail.send(new MailService.Mail(email, "Tu contraseña de CIVIA AI cambió", """
                        Hola, %s:

                        La contraseña de tu cuenta de CIVIA AI se restableció y se cerraron todas tus sesiones.
                        Si no fuiste tú, restablécela de nuevo de inmediato y activa la verificación en dos pasos.

                        — CIVIA Auth
                        """.formatted(name)));
            } catch (Exception e) {
                LOG.error("No se pudo enviar la confirmación de cambio de contraseña", e);
            }
        });
    }
}
