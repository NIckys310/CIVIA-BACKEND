package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Intentos de inicio de sesión (auditoría y bloqueo). */
@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt extends BaseEntity {

    @Column(columnDefinition = "text") private String email;

    private String ipAddress;

    private boolean success;

    private String reason;
}
