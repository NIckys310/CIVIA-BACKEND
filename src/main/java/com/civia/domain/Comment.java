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

/** Comentario sobre el proyecto o uno de sus elementos. */
@Entity
@Table(name = "comments", indexes = {@Index(name = "ix_comments_project", columnList = "project_id")})
@Getter
@Setter
@NoArgsConstructor
public class Comment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User author;

    private String targetType;

    private UUID targetId;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String body;
}
