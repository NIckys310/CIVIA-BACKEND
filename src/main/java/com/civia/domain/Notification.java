package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Notificación para un usuario. */
@Entity
@Table(name = "notifications", indexes = {@Index(name = "ix_notifications_user", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User user;

    private String type;

    @Column(columnDefinition = "text") private String title;

    @Column(columnDefinition = "text") private String body;

    @Column(columnDefinition = "text") private String link;

    private Instant readAt;
}
