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

/** Bitácora de actividad del proyecto. */
@Entity
@Table(name = "activity_log", indexes = {@Index(name = "ix_activity_project", columnList = "project_id")})
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) private User user;

    @Column(nullable = false) private String action;

    private String entityType;

    private UUID entityId;

    @JdbcTypeCode(LONG32VARCHAR) private String detail;
}
