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

/** Nivel o piso del proyecto. */
@Entity
@Table(name = "levels")
@Getter
@Setter
@NoArgsConstructor
public class Level extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String name;

    private Double elevation;

    private Double height;

    private int sortOrder;
}
