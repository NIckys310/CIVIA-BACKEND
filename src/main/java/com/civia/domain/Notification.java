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

/** Notificación para un usuario. */
@Entity
@Table(name = "notifications", indexes = {@Index(name = "ix_notifications_user", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User user;

    private String type;

    @JdbcTypeCode(LONG32VARCHAR) private String title;

    @JdbcTypeCode(LONG32VARCHAR) private String body;

    @JdbcTypeCode(LONG32VARCHAR) private String link;

    private Instant readAt;
}
