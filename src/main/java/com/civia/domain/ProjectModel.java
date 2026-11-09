package com.civia.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Modelo 3D paramétrico de un proyecto, hecho en el modelador de CIVIA: columnas, vigas, muros,
 * losas, zapatas, puertas, ventanas, escaleras y cubiertas, guardado como JSON.
 */
@Entity
@Table(name = "project_models", uniqueConstraints = @UniqueConstraint(name = "uq_project_model", columnNames = "project_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProjectModel extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @Column(columnDefinition = "text", nullable = false) private String json;

    /** Número de guardados: evita pisar los cambios de otra persona (control optimista). */
    @Column(nullable = false, columnDefinition = "integer default 0") private int revision;

    @ManyToOne(fetch = FetchType.LAZY) private User updatedBy;
}
