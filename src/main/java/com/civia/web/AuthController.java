package com.civia.web;

import com.civia.domain.RefreshToken;
import com.civia.domain.User;
import com.civia.domain.UserPreference;
import com.civia.domain.UserProfile;
import com.civia.repo.SecurityEventRepository;
import com.civia.repo.UserPreferenceRepository;
import com.civia.repo.UserProfileRepository;
import com.civia.security.PasswordService;
import com.civia.security.TokenService;
import com.civia.service.AuditService;
import com.civia.service.AuthService;
import com.civia.service.PasswordResetService;
import com.civia.service.ProjectAccess;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * <b>CIVIA Auth</b>: identidad y sesiones al estilo OAuth 2.0.
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/token} con {@code grant_type=password} o {@code refresh_token}.</li>
 *   <li>{@code POST /api/v1/auth/revoke} cierra la sesión del dispositivo.</li>
 *   <li>{@code GET/PATCH /api/v1/auth/user} es la cuenta autenticada.</li>
 *   <li>{@code POST /api/v1/auth/password/forgot} y {@code /password/reset} recuperan la cuenta por correo.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "CIVIA Auth")
public class AuthController {

    static final String REFRESH_COOKIE = "civia_rt";

    private final AuthService auth;
    private final TokenService tokens;
    private final PasswordService passwords;
    private final ProjectAccess access;
    private final UserProfileRepository profiles;
    private final UserPreferenceRepository preferences;
    private final SecurityEventRepository securityEvents;
    private final AuditService audit;
    private final PasswordResetService resets;
    private final boolean secureCookies;

    public AuthController(AuthService auth, TokenService tokens, PasswordService passwords, ProjectAccess access,
                          UserProfileRepository profiles, UserPreferenceRepository preferences,
                          SecurityEventRepository securityEvents, AuditService audit, PasswordResetService resets,
                          @Value("${civia.security.secure-cookies:true}") boolean secureCookies) {
        this.auth = auth;
        this.tokens = tokens;
        this.passwords = passwords;
        this.access = access;
        this.profiles = profiles;
        this.preferences = preferences;
        this.securityEvents = securityEvents;
        this.audit = audit;
        this.resets = resets;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@RequestBody RegisterRequest req, HttpServletRequest http, HttpServletResponse res) {
        AuthService.LoginResult r = auth.register(req.fullName(), req.email(), req.password(), req.profession(),
                req.organization(), agent(http), http.getRemoteAddr());
        return respond(r.user(), r.tokens(), res);
    }

    @PostMapping("/token")
    public TokenResponse token(@RequestBody TokenRequest req, @CookieValue(name = REFRESH_COOKIE, required = false) String refresh,
                               HttpServletRequest http, HttpServletResponse res) {
        String grant = req.grant_type() == null ? "password" : req.grant_type();
        switch (grant) {
            case "password" -> {
                AuthService.LoginResult r = auth.login(req.email(), req.password(), req.mfa_code(), agent(http), http.getRemoteAddr());
                if (r.mfaRequired()) return TokenResponse.mfa();
                return respond(r.user(), r.tokens(), res);
            }
            case "refresh_token" -> {
                TokenService.Rotation rot = tokens.rotate(refresh, agent(http), http.getRemoteAddr())
                        .orElseThrow(() -> {
                            clearCookie(res);
                            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión de nuevo.");
                        });
                return respond(rot.user(), rot.tokens(), res);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "grant_type no soportado");
        }
    }

    @PostMapping("/revoke")
    public Map<String, Object> revoke(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh, HttpServletResponse res) {
        tokens.revoke(refresh);
        clearCookie(res);
        return Map.of("revoked", true);
    }

    @GetMapping("/password-policy")
    public Map<String, Object> passwordPolicy() {
        return Map.of("minLength", PasswordService.MIN_LENGTH, "maxLength", "sin límite",
                "hash", "Argon2id", "rules", List.of("Al menos 8 caracteres", "Letras y números o símbolos (o frase de 12+)", "No reutilizar las últimas 5"));
    }

    @GetMapping("/user")
    @Transactional(readOnly = true)
    public UserDto user() {
        return toDto(access.me());
    }

    @PatchMapping("/user")
    @Transactional
    public UserDto update(@RequestBody UpdateUserRequest req) {
        User u = access.me();
        if (req.fullName() != null && !req.fullName().isBlank()) u.setFullName(req.fullName().strip());
        if (req.phone() != null) u.setPhone(req.phone().strip());
        if (req.profession() != null) u.setProfession(req.profession().strip());
        UserProfile p = profiles.findByUserId(u.getId()).orElseGet(() -> {
            UserProfile n = new UserProfile();
            n.setUser(u);
            return profiles.save(n);
        });
        if (req.profile() != null) {
            ProfileDto d = req.profile();
            if (d.bio() != null) p.setBio(d.bio());
            if (d.company() != null) p.setCompany(d.company());
            if (d.professionalId() != null) p.setProfessionalId(d.professionalId());
            if (d.city() != null) p.setCity(d.city());
            if (d.country() != null) p.setCountry(d.country());
            if (d.website() != null) p.setWebsite(d.website());
        }
        UserPreference pref = preferences.findByUserId(u.getId()).orElseGet(() -> {
            UserPreference n = new UserPreference();
            n.setUser(u);
            return preferences.save(n);
        });
        if (req.preferences() != null) {
            PreferencesDto d = req.preferences();
            if (d.theme() != null) pref.setTheme(d.theme());
            if (d.language() != null) pref.setLanguage(d.language());
            if (d.units() != null) pref.setUnits(d.units());
            if (d.emailNotifications() != null) pref.setEmailNotifications(d.emailNotifications());
            if (d.compactMode() != null) pref.setCompactMode(d.compactMode());
        }
        return toDto(u);
    }

    @PostMapping("/password")
    public TokenResponse changePassword(@RequestBody ChangePasswordRequest req, HttpServletRequest http, HttpServletResponse res) {
        User u = access.me();
        TokenService.Issued issued = auth.changePassword(u, req.currentPassword(), req.newPassword(), agent(http), http.getRemoteAddr());
        return respond(u, issued, res);
    }

    /** Siempre la misma respuesta (202), exista o no la cuenta. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> forgotPassword(@RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        resets.requestReset(req.email(), http.getRemoteAddr(), agent(http));
        return Map.of("message", "Si existe una cuenta con ese correo, te enviamos un enlace para restablecer la contraseña. Revisa tu bandeja de entrada.");
    }

    @PostMapping("/password/reset")
    public Map<String, Object> resetPassword(@RequestBody ResetPasswordRequest req, HttpServletRequest http, HttpServletResponse res) {
        resets.reset(req.token(), req.password(), http.getRemoteAddr(), agent(http));
        clearCookie(res);
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return Map.of("reset", true, "message", "Contraseña actualizada. Inicia sesión con la nueva.");
    }

    @PostMapping("/password/check")
    public Map<String, Object> checkPassword(@RequestBody Map<String, String> body) {
        List<String> problems = passwords.problems(body.get("password"), body.get("email"), null);
        return Map.of("valid", problems.isEmpty(), "problems", problems);
    }

    @GetMapping("/sessions")
    public List<SessionDto> sessions() {
        return auth.activeSessions(access.me().getId()).stream()
                .map(t -> new SessionDto(t.getId(), t.getUserAgent(), t.getIpAddress(), t.getCreatedAt(), t.getLastUsedAt(), t.getExpiresAt()))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    @Transactional
    public Map<String, Object> closeSession(@PathVariable UUID id) {
        User me = access.me();
        RefreshToken t = auth.activeSessions(me.getId()).stream().filter(s -> s.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada"));
        tokens.revokeFamily(t.getFamilyId());
        audit.security(me, "session-closed", null, null, "Sesión cerrada desde otro dispositivo");
        return Map.of("closed", true);
    }

    @PostMapping("/sessions/revoke-all")
    public Map<String, Object> closeAll(HttpServletResponse res) {
        User me = access.me();
        tokens.revokeAll(me.getId());
        clearCookie(res);
        audit.security(me, "sessions-closed", null, null, "Se cerraron todas las sesiones");
        return Map.of("closed", true);
    }

    @GetMapping("/security-events")
    public List<SecurityEventDto> securityEvents() {
        return securityEvents.findTop50ByUserIdOrderByCreatedAtDesc(access.me().getId()).stream()
                .map(e -> new SecurityEventDto(e.getType(), e.getDetail(), e.getIpAddress(), e.getUserAgent(), e.getCreatedAt()))
                .toList();
    }

    @PostMapping("/mfa/setup")
    public AuthService.MfaSetup mfaSetup() {
        return auth.startMfa(access.me());
    }

    @PostMapping("/mfa/enable")
    public Map<String, Object> mfaEnable(@RequestBody MfaCodeRequest req, HttpServletRequest http) {
        return Map.of("enabled", true, "recoveryCodes", auth.enableMfa(access.me(), req.code(), http.getRemoteAddr(), agent(http)));
    }

    @PostMapping("/mfa/disable")
    public Map<String, Object> mfaDisable(@RequestBody PasswordConfirmRequest req, HttpServletRequest http) {
        auth.disableMfa(access.me(), req.password(), http.getRemoteAddr(), agent(http));
        return Map.of("enabled", false);
    }

    // ------------------------------------------------------------------ utilidades

    private TokenResponse respond(User user, TokenService.Issued issued, HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, issued.refreshToken())
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.between(Instant.now(), issued.refreshExpiresAt()))
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return TokenResponse.bearer(issued.accessToken(), issued.expiresIn(), toDto(user));
    }

    private void clearCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(secureCookies)
                .sameSite("Strict").path("/api/v1/auth").maxAge(0).build().toString());
    }

    private UserDto toDto(User u) {
        return Dto.user(u, profiles.findByUserId(u.getId()).orElse(null), preferences.findByUserId(u.getId()).orElse(null));
    }

    private static String agent(HttpServletRequest http) {
        String ua = http.getHeader(HttpHeaders.USER_AGENT);
        return ua == null ? "desconocido" : ua;
    }
}
