package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Análisis de IA de un plano, modelo o proyecto. */
@Entity
@Table(name = "ai_analyses", indexes = {@Index(name = "ix_ai_analyses_project", columnList = "project_id")})
@Getter
@Setter
@NoArgsConstructor
public class AiAnalysis extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) private Document document;

    @Column(nullable = false) private String analysisType;

    private String engine;

    @Column(columnDefinition = "text") private String summary;

    private Integer completeness;

    /** Resultado completo (JSON) para volver a mostrarlo sin recalcular. */
    @Column(columnDefinition = "text") private String resultJson;

    @ManyToOne(fetch = FetchType.LAZY) private User createdBy;
}
