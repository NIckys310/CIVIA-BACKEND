package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hallazgo individual de un análisis. */
@Entity
@Table(name = "ai_findings")
@Getter
@Setter
@NoArgsConstructor
public class AiFinding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private AiAnalysis analysis;

    private String checkId;

    @Column(columnDefinition = "text") private String title;

    private String status;

    @Column(columnDefinition = "text") private String detail;

    @Column(columnDefinition = "text") private String recommendation;

    private boolean resolved;
}
