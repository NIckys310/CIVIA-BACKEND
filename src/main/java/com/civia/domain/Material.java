package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catálogo de materiales con precio de referencia. */
@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
public class Material extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Organization organization;

    @Column(columnDefinition = "text", nullable = false) private String name;

    @Column(columnDefinition = "text") private String category;

    private String unit;

    @Column(precision = 19, scale = 2) private BigDecimal unitPrice;

    @ManyToOne(fetch = FetchType.LAZY) private Supplier supplier;
}
