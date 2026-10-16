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

/** Auditoría append-only encadenada con SHA-256. */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    private UUID userId;

    @Column(nullable = false) private String action;

    private String ipAddress;

    @JdbcTypeCode(LONG32VARCHAR) private String detail;

    /** Cadena de hashes: alterar un registro rompe todos los siguientes. */
    @Column(length = 64) private String prevHash;

    @Column(nullable = false, length = 64) private String hash;
}
