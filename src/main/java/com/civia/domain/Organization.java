package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Organización (estudio de arquitectura o empresa). */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization extends BaseEntity {

    @Column(columnDefinition = "text", nullable = false) private String name;

    @Column(nullable = false, unique = true, length = 160) private String slug;

    private String plan = "pro";
}
