package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fase del proyecto (concepto, anteproyecto, licencia, obra…). */
@Entity
@Table(name = "project_phases")
@Getter
@Setter
@NoArgsConstructor
public class ProjectPhase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @Column(columnDefinition = "text", nullable = false) private String name;

    private int sortOrder;

    private String status = "PENDIENTE";

    private LocalDate startDate;

    private LocalDate endDate;
}
