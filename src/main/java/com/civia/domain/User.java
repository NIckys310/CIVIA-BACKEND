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

/** Usuario. La contraseña solo existe como hash Argon2id. */
@Entity
@Table(name = "users", indexes = {@Index(name = "ix_users_org", columnList = "organization_id")})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /** Correo normalizado (minúsculas). */
    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false, unique = true) private String email;

    /** Hash Argon2id; nunca la contraseña. */
    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String passwordHash;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String fullName;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @JdbcTypeCode(LONG32VARCHAR) private String phone;

    @JdbcTypeCode(LONG32VARCHAR) private String profession;

    private String avatarColor;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private Enums.UserRole role = Enums.UserRole.ARCHITECT;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private Enums.UserStatus status = Enums.UserStatus.ACTIVE;

    private boolean emailVerified;

    private int failedLogins;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    private Instant passwordChangedAt;

    private boolean mfaEnabled;

    /** Semilla TOTP cifrada con AES-256-GCM. */
    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @JdbcTypeCode(LONG32VARCHAR) private String mfaSecret;

    @ManyToOne(fetch = FetchType.EAGER) private Organization organization;
}
