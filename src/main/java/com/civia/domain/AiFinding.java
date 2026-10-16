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

/** Hallazgo individual de un análisis. */
@Entity
@Table(name = "ai_findings")
@Getter
@Setter
@NoArgsConstructor
public class AiFinding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private AiAnalysis analysis;

    private String checkId;

    @JdbcTypeCode(LONG32VARCHAR) private String title;

    private String status;

    @JdbcTypeCode(LONG32VARCHAR) private String detail;

    @JdbcTypeCode(LONG32VARCHAR) private String recommendation;

    private boolean resolved;
}
