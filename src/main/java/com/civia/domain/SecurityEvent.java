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

/** Historial de seguridad visible para el usuario. */
@Entity
@Table(name = "security_events", indexes = {@Index(name = "ix_security_events_user", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
public class SecurityEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private User user;

    @Column(nullable = false) private String type;

    private String ipAddress;

    @JdbcTypeCode(LONG32VARCHAR) private String userAgent;

    @JdbcTypeCode(LONG32VARCHAR) private String detail;
}
