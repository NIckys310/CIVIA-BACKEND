package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Porcentajes de AIU del presupuesto. */
@Entity
@Table(name = "budget_settings")
@Getter
@Setter
@NoArgsConstructor
public class BudgetSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true) private Project project;

    @Column(precision = 7, scale = 3) private BigDecimal administrationPct = new BigDecimal("10");

    @Column(precision = 7, scale = 3) private BigDecimal contingencyPct = new BigDecimal("5");

    @Column(precision = 7, scale = 3) private BigDecimal profitPct = new BigDecimal("5");

    @Column(precision = 7, scale = 3) private BigDecimal taxOnProfitPct = new BigDecimal("19");
}
