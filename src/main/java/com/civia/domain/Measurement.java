package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Medición estimada sobre una foto con referencia. */
@Entity
@Table(name = "measurements")
@Getter
@Setter
@NoArgsConstructor
public class Measurement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) private Document document;

    @ManyToOne(fetch = FetchType.LAZY) private User author;

    @Column(columnDefinition = "text") private String label;

    @Column(name = "measured_value") private double value;

    private String unit = "m";

    private Double referenceLength;

    /** Puntos marcados sobre la imagen (JSON). */
    @Column(columnDefinition = "text") private String pointsJson;
}
