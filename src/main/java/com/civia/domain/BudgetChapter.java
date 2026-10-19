package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Capítulo del presupuesto. */
@Entity
@Table(name = "budget_chapters")
@Getter
@Setter
@NoArgsConstructor
public class BudgetChapter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    private String code;

    @Column(columnDefinition = "text", nullable = false) private String name;

    private int sortOrder;
}
