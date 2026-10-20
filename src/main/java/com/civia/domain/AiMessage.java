package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mensaje de una conversación. */
@Entity
@Table(name = "ai_messages", indexes = {@Index(name = "ix_ai_msg_conv", columnList = "conversation_id")})
@Getter
@Setter
@NoArgsConstructor
public class AiMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private AiConversation conversation;

    @Column(nullable = false) private String role;

    @Column(columnDefinition = "text", nullable = false) private String content;

    @Column(columnDefinition = "text") private String sourcesJson;

    private String engine;

    /** Valoración de quien preguntó: 1 útil, -1 no útil, null sin valorar. */
    private Integer feedback;
}
