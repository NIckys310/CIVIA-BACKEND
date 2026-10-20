package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Conversación con CIVIA Copilot. */
@Entity
@Table(name = "ai_conversations", indexes = {@Index(name = "ix_ai_conv_user", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
public class AiConversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User user;

    @ManyToOne(fetch = FetchType.LAZY) private Project project;

    @Column(columnDefinition = "text") private String title;

    /** Fecha del último mensaje: ordena la lista de conversaciones. */
    private Instant lastMessageAt;
}
