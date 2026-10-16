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

/** Ítem del presupuesto. */
@Entity
@Table(name = "budget_items")
@Getter
@Setter
@NoArgsConstructor
public class BudgetItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private BudgetChapter chapter;

    private String code;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String description;

    private String unit = "und";

    @Column(precision = 19, scale = 4) private BigDecimal quantity = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2) private BigDecimal unitPrice = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY) private Material material;
}
