package com.civia.security;

import com.civia.domain.RefreshToken;
import com.civia.domain.User;
import com.civia.repo.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite tokens al estilo OAuth 2.0:
 * <ul>
 *   <li><b>access_token</b>: JWT HS256 de vida corta, solo en memoria del navegador.</li>
 *   <li><b>refresh_token</b>: aleatorio de 256 bits en cookie HttpOnly; en la base solo su SHA-256.
 *       Rota en cada uso y, si se reutiliza uno ya rotado (robo), se revoca toda la familia.</li>
 * </ul>
 */
@Service
public class TokenService {

    public static final String ISSUER = "civia-auth";

    public record Issued(String accessToken, long expiresIn, String refreshToken, Instant refreshExpiresAt) {}

    /** Resultado de rotar: el usuario y los nuevos tokens, o vacío si el token no es válido. */
    public record Rotation(User user, Issued tokens) {}

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens,
                        @Value("${civia.security.access-token-minutes:15}") long accessMinutes,
                        @Value("${civia.security.refresh-token-days:30}") long refreshDays) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    public String accessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Transactional
    public Issued issue(User user, String userAgent, String ip) {
        return issueInFamily(user, UUID.randomUUID(), userAgent, ip);
    }

    private Issued issueInFamily(User user, UUID family, String userAgent, String ip) {
        String raw = Hashing.randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(Hashing.sha256(raw));
        rt.setFamilyId(family);
        rt.setExpiresAt(Instant.now().plus(refreshTtl));
        rt.setUserAgent(userAgent);
        rt.setIpAddress(ip);
        rt.setLastUsedAt(Instant.now());
        refreshTokens.save(rt);
        return new Issued(accessToken(user), accessTtl.toSeconds(), raw, rt.getExpiresAt());
    }

    /** Rota el refresh token. Detecta reutilización y revoca la familia completa. */
    @Transactional
    public Optional<Rotation> rotate(String raw, String userAgent, String ip) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Optional<RefreshToken> found = refreshTokens.findByTokenHash(Hashing.sha256(raw));
        if (found.isEmpty()) return Optional.empty();
        RefreshToken rt = found.get();
        Instant now = Instant.now();
        if (rt.getRevokedAt() != null) {
            if (rt.getReplacedByHash() != null) {
                // Token ya usado: alguien lo copió. Se cierra la sesión en ese dispositivo.
                revokeFamily(rt.getFamilyId());
            }
            return Optional.empty();
        }
        if (rt.getExpiresAt().isBefore(now)) return Optional.empty();
        User user = rt.getUser();
        if (user.getStatus() != com.civia.domain.Enums.UserStatus.ACTIVE) return Optional.empty();
        Issued next = issueInFamily(user, rt.getFamilyId(), userAgent, ip);
        rt.setRevokedAt(now);
        rt.setReplacedByHash(Hashing.sha256(next.refreshToken()));
        return Optional.of(new Rotation(user, next));
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) return;
        refreshTokens.findByTokenHash(Hashing.sha256(raw)).ifPresent(rt -> revokeFamily(rt.getFamilyId()));
    }

    @Transactional
    public void revokeFamily(UUID family) {
        Instant now = Instant.now();
        refreshTokens.findByFamilyId(family).forEach(t -> {
            if (t.getRevokedAt() == null) t.setRevokedAt(now);
        });
    }

    @Transactional
    public void revokeAll(UUID userId) {
        Instant now = Instant.now();
        refreshTokens.findByUserIdAndRevokedAtIsNull(userId).forEach(t -> t.setRevokedAt(now));
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }
}
