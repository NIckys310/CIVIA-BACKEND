package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nivel o piso del proyecto. */
@Entity
@Table(name = "levels")
@Getter
@Setter
@NoArgsConstructor
public class Level extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @Column(columnDefinition = "text", nullable = false) private String name;

    private Double elevation;

    private Double height;

    private int sortOrder;
}
