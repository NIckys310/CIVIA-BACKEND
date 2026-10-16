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

/** Conversación con CIVIA Copilot. */
@Entity
@Table(name = "ai_conversations", indexes = {@Index(name = "ix_ai_conv_user", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
public class AiConversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User user;

    @ManyToOne(fetch = FetchType.LAZY) private Project project;

    @JdbcTypeCode(LONG32VARCHAR) private String title;
}
