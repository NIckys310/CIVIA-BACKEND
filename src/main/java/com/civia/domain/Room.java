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

/** Espacio del programa arquitectónico. */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) private Level level;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String name;

    @JdbcTypeCode(LONG32VARCHAR) private String roomType;

    private Double area;

    @JdbcTypeCode(LONG32VARCHAR) private String notes;
}
