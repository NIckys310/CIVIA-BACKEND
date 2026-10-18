package com.civia.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Token de un solo uso enviado por correo (p. ej. recuperación de contraseña). Solo se guarda su
 * SHA-256: quien lea la base no puede usar los enlaces pendientes.
 */
@Entity
@Table(name = "user_tokens", indexes = {@Index(name = "ix_ut_user_purpose", columnList = "user_id,purpose")})
@Getter
@Setter
@NoArgsConstructor
public class UserToken extends BaseEntity {

    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User user;

    @Column(nullable = false, length = 32) private String purpose;

    @Column(nullable = false, unique = true, length = 64) private String tokenHash;

    @Column(nullable = false) private Instant expiresAt;

    private Instant usedAt;

    private String ipAddress;
}
