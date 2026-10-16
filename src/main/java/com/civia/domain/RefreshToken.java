package com.civia.domain;

import static org.hibernate.type.SqlTypes.LONG32VARCHAR;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

/** Token de refresco rotativo (solo se guarda su SHA-256). */
@Entity
@Table(name = "refresh_tokens", indexes = {@Index(name = "ix_rt_family", columnList = "familyId"), @Index(name = "ix_rt_user", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User user;

    @Column(nullable = false, unique = true, length = 64) private String tokenHash;

    /** Familia de rotación: reutilizar un token ya rotado revoca toda la familia. */
    @Column(nullable = false) private UUID familyId;

    @Column(nullable = false) private Instant expiresAt;

    private Instant revokedAt;

    private Instant lastUsedAt;

    @Column(length = 64) private String replacedByHash;

    @JdbcTypeCode(LONG32VARCHAR) private String userAgent;

    private String ipAddress;
}
