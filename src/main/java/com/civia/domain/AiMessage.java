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

/** Mensaje de una conversación. */
@Entity
@Table(name = "ai_messages", indexes = {@Index(name = "ix_ai_msg_conv", columnList = "conversation_id")})
@Getter
@Setter
@NoArgsConstructor
public class AiMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private AiConversation conversation;

    @Column(nullable = false) private String role;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String content;

    @JdbcTypeCode(LONG32VARCHAR) private String sourcesJson;

    private String engine;
}
