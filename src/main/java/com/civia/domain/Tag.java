package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Etiqueta para clasificar proyectos. */
@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
public class Tag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Organization organization;

    @Column(columnDefinition = "text", nullable = false) private String name;

    private String color;
}
