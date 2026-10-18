package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Espacio del programa arquitectónico. */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) private Level level;

    @Column(columnDefinition = "text", nullable = false) private String name;

    @Column(columnDefinition = "text") private String roomType;

    private Double area;

    @Column(columnDefinition = "text") private String notes;
}
