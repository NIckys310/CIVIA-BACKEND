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

/** Nota anclada a un punto de un modelo 3D. */
@Entity
@Table(name = "model_annotations")
@Getter
@Setter
@NoArgsConstructor
public class ModelAnnotation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Document document;

    @ManyToOne(fetch = FetchType.LAZY) private User author;

    private double x;

    private double y;

    private double z;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String text;

    private boolean resolved;
}
