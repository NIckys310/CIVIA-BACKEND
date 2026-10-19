package com.civia.service;

import com.civia.domain.Enums;
import com.civia.domain.Membership;
import com.civia.domain.MfaRecoveryCode;
import com.civia.domain.Organization;
import com.civia.domain.RefreshToken;
import com.civia.domain.User;
import com.civia.domain.UserPreference;
import com.civia.domain.UserProfile;
import com.civia.domain.LoginAttempt;
import com.civia.repo.LoginAttemptRepository;
import com.civia.repo.MembershipRepository;
import com.civia.repo.MfaRecoveryCodeRepository;
import com.civia.repo.OrganizationRepository;
import com.civia.repo.RefreshTokenRepository;
import com.civia.repo.UserPreferenceRepository;
import com.civia.repo.UserProfileRepository;
import com.civia.repo.UserRepository;
import com.civia.security.Hashing;
import com.civia.security.PasswordService;
import com.civia.security.TokenService;
import com.civia.security.Totp;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** CIVIA Auth: registro, inicio de sesión, bloqueo, verificación en dos pasos y sesiones. */
@Service
public class AuthService {

    /** Resultado de un inicio de sesión: tokens, o la indicación de que falta el segundo factor. */
    public record LoginResult(User user, TokenService.Issued tokens, boolean mfaRequired) {}

    public record MfaSetup(String secret, String otpauthUri) {}

    private static final String[] AVATAR_COLORS = {"#C9A46A", "#8FA3C7", "#B98B6E", "#7FA08F", "#A68FBF", "#C7A08F"};
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final UserPreferenceRepository preferences;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final LoginAttemptRepository attempts;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordService passwords;
    private final TokenService tokens;
    private final AuditService audit;
    private final int maxFailed;
    private final Duration lockout;
    /** Hash ficticio para igualar el tiempo de respuesta cuando el correo no existe. */
    private final String dummyHash;

    public AuthService(UserRepository users, UserProfileRepository profiles, UserPreferenceRepository preferences,
                       OrganizationRepository organizations, MembershipRepository memberships,
                       LoginAttemptRepository attempts, MfaRecoveryCodeRepository recoveryCodes,
                       RefreshTokenRepository refreshTokens, PasswordService passwords, TokenService tokens,
                       AuditService audit,
                       @Value("${civia.security.max-failed-logins:5}") int maxFailed,
                       @Value("${civia.security.lockout-minutes:15}") long lockoutMinutes) {
        this.users = users;
        this.profiles = profiles;
        this.preferences = preferences;
        this.organizations = organizations;
        this.memberships = memberships;
        this.attempts = attempts;
        this.recoveryCodes = recoveryCodes;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.audit = audit;
        this.maxFailed = maxFailed;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
        this.dummyHash = passwords.hash(Hashing.randomToken());
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public LoginResult register(String fullName, String email, String password, String profession, String orgName,
                                String userAgent, String ip) {
        String normalized = normalizeEmail(email);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo electrónico inválido.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe tu nombre.");
        }
        passwords.requireStrong(password, normalized, fullName);
        if (users.existsByEmail(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
        }

        Organization org = new Organization();
        org.setName(orgName == null || orgName.isBlank() ? "Estudio de " + fullName.strip() : orgName.strip());
        org.setSlug(uniqueSlug(org.getName()));
        organizations.save(org);

        User user = new User();
        user.setEmail(normalized);
        user.setFullName(fullName.strip());
        user.setProfession(profession == null || profession.isBlank() ? "Arquitecto(a)" : profession.strip());
        user.setPasswordHash(passwords.hash(password));
        user.setPasswordChangedAt(Instant.now());
        user.setAvatarColor(AVATAR_COLORS[RANDOM.nextInt(AVATAR_COLORS.length)]);
        user.setRole(Enums.UserRole.ARCHITECT);
        user.setOrganization(org);
        users.save(user);

        Membership m = new Membership();
        m.setOrganization(org);
        m.setUser(user);
        m.setRole("OWNER");
        memberships.save(m);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profiles.save(profile);
        UserPreference prefs = new UserPreference();
        prefs.setUser(user);
        preferences.save(prefs);

        audit.security(user, "register", ip, userAgent, "Cuenta creada");
        audit.notify(user, "welcome", "Bienvenido(a) a CIVIA AI",
                "Crea tu primer proyecto, sube planos y conversa con CIVIA Copilot.", "#/projects");
        user.setLastLoginAt(Instant.now());
        return new LoginResult(user, tokens.issue(user, userAgent, ip), false);
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "estudio";
        if (base.length() > 120) base = base.substring(0, 120);
        String slug = base;
        while (organizations.existsBySlug(slug)) {
            slug = base + "-" + Integer.toHexString(RANDOM.nextInt(0xfffff));
        }
        return slug;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String email, String password, String mfaCode, String userAgent, String ip) {
        String normalized = normalizeEmail(email);
        if (attempts.countByIpAddressAndSuccessFalseAndCreatedAtAfter(ip, Instant.now().minus(Duration.ofMinutes(15))) > 50) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos fallidos desde esta red. Espera unos minutos.");
        }
        User user = users.findByEmail(normalized).orElse(null);
        if (user == null) {
            passwords.matches(password, dummyHash);
            record(normalized, ip, false, "unknown-email");
            throw invalidCredentials();
        }
        if (user.getStatus() == Enums.UserStatus.DISABLED) {
            record(normalized, ip, false, "disabled");
            throw invalidCredentials();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            record(normalized, ip, false, "locked");
            long minutes = Math.max(1, Duration.between(Instant.now(), user.getLockedUntil()).toMinutes() + 1);
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "Cuenta bloqueada temporalmente por intentos fallidos. Intenta en " + minutes + " min.");
        }
        if (!passwords.matches(password, user.getPasswordHash())) {
            user.setFailedLogins(user.getFailedLogins() + 1);
            if (user.getFailedLogins() >= maxFailed) {
                user.setLockedUntil(Instant.now().plus(lockout));
                user.setFailedLogins(0);
                audit.security(user, "locked", ip, userAgent, "Bloqueo por " + maxFailed + " intentos fallidos");
            }
            record(normalized, ip, false, "bad-password");
            throw invalidCredentials();
        }
        if (user.isMfaEnabled()) {
            if (mfaCode == null || mfaCode.isBlank()) {
                return new LoginResult(user, null, true);
            }
            if (!verifySecondFactor(user, mfaCode)) {
                user.setFailedLogins(user.getFailedLogins() + 1);
                record(normalized, ip, false, "bad-mfa");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código de verificación incorrecto.");
            }
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        boolean newDevice = refreshTokens.findByUserIdAndRevokedAtIsNull(user.getId()).stream()
                .noneMatch(t -> userAgent != null && userAgent.equals(t.getUserAgent()));
        user.setLastLoginAt(Instant.now());
        record(normalized, ip, true, "ok");
        audit.security(user, "login", ip, userAgent, newDevice ? "Inicio de sesión desde un dispositivo nuevo" : "Inicio de sesión");
        if (newDevice) {
            audit.notify(user, "security", "Nuevo inicio de sesión",
                    "Se inició sesión desde un dispositivo nuevo (" + ip + "). Si no fuiste tú, cambia tu contraseña.", "#/profile");
        }
        return new LoginResult(user, tokens.issue(user, userAgent, ip), false);
    }

    private boolean verifySecondFactor(User user, String code) {
        String clean = code.replaceAll("[\\s-]", "");
        if (clean.matches("\\d{6}")) {
            return Totp.verify(user.getMfaSecret(), clean, Instant.now());
        }
        String hash = Hashing.sha256(clean.toUpperCase(Locale.ROOT));
        for (MfaRecoveryCode rc : recoveryCodes.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (Hashing.constantTimeEquals(rc.getCodeHash(), hash)) {
                rc.setUsedAt(Instant.now());
                return true;
            }
        }
        return false;
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
    }

    private void record(String email, String ip, boolean success, String reason) {
        LoginAttempt a = new LoginAttempt();
        a.setEmail(email);
        a.setIpAddress(ip);
        a.setSuccess(success);
        a.setReason(reason);
        attempts.save(a);
    }

    @Transactional
    public TokenService.Issued changePassword(User user, String current, String next, String userAgent, String ip) {
        if (!passwords.matches(current, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
        }
        passwords.requireStrong(next, user.getEmail(), user.getFullName());
        passwords.requireNotReused(user, next);
        passwords.remember(user);
        user.setPasswordHash(passwords.hash(next));
        user.setPasswordChangedAt(Instant.now());
        tokens.revokeAll(user.getId());
        audit.security(user, "password-changed", ip, userAgent, "Contraseña cambiada; se cerraron las demás sesiones");
        return tokens.issue(user, userAgent, ip);
    }

    @Transactional
    public MfaSetup startMfa(User user) {
        if (user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La verificación en dos pasos ya está activa.");
        }
        String secret = Totp.newSecret();
        user.setMfaSecret(secret);
        return new MfaSetup(secret, Totp.otpauthUri(secret, user.getEmail()));
    }

    /** Activa MFA y devuelve los códigos de recuperación (se muestran una sola vez). */
    @Transactional
    public List<String> enableMfa(User user, String code, String ip, String userAgent) {
        if (user.getMfaSecret() == null || !Totp.verify(user.getMfaSecret(), code, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorrecto. Revisa la hora de tu teléfono.");
        }
        user.setMfaEnabled(true);
        recoveryCodes.deleteByUserId(user.getId());
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String c = Hashing.randomToken().replaceAll("[^A-Za-z0-9]", "").substring(0, 10).toUpperCase(Locale.ROOT);
            codes.add(c.substring(0, 5) + "-" + c.substring(5));
            MfaRecoveryCode rc = new MfaRecoveryCode();
            rc.setUser(user);
            rc.setCodeHash(Hashing.sha256(c));
            recoveryCodes.save(rc);
        }
        audit.security(user, "mfa-enabled", ip, userAgent, "Verificación en dos pasos activada");
        return codes;
    }

    @Transactional
    public void disableMfa(User user, String password, String ip, String userAgent) {
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contraseña incorrecta.");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        recoveryCodes.deleteByUserId(user.getId());
        audit.security(user, "mfa-disabled", ip, userAgent, "Verificación en dos pasos desactivada");
    }

    public List<RefreshToken> activeSessions(UUID userId) {
        return refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now());
    }
}
