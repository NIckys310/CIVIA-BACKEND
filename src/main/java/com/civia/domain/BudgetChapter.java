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

/** Capítulo del presupuesto. */
@Entity
@Table(name = "budget_chapters")
@Getter
@Setter
@NoArgsConstructor
public class BudgetChapter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    private String code;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String name;

    private int sortOrder;
}
