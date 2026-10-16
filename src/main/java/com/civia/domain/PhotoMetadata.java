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

/** Datos de una foto tomada con la cámara de CIVIA. */
@Entity
@Table(name = "photo_metadata")
@Getter
@Setter
@NoArgsConstructor
public class PhotoMetadata extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true) private Document document;

    private Double latitude;

    private Double longitude;

    private Instant takenAt;

    @JdbcTypeCode(LONG32VARCHAR) private String deviceInfo;

    @JdbcTypeCode(LONG32VARCHAR) private String note;
}
