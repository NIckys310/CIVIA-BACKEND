package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Usuario. La contraseña solo existe como hash Argon2id. */
@Entity
@Table(name = "users", indexes = {@Index(name = "ix_users_org", columnList = "organization_id")})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /** Correo normalizado (minúsculas). */
    @Column(columnDefinition = "text", nullable = false, unique = true) private String email;

    /** Hash Argon2id; nunca la contraseña. */
    @Column(columnDefinition = "text", nullable = false) private String passwordHash;

    @Column(columnDefinition = "text", nullable = false) private String fullName;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @Column(columnDefinition = "text") private String phone;

    @Column(columnDefinition = "text") private String profession;

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
    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @Column(columnDefinition = "text") private String mfaSecret;

    @ManyToOne(fetch = FetchType.EAGER) private Organization organization;
}
