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

/** Archivo del proyecto: plano, modelo 3D, foto o documento. */
@Entity
@Table(name = "documents", indexes = {@Index(name = "ix_documents_project", columnList = "project_id")})
@Getter
@Setter
@NoArgsConstructor
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY) private User uploadedBy;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String name;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private Enums.DocumentKind kind;

    private String mediaType;

    private String extension;

    private long sizeBytes;

    @Column(length = 64) private String sha256;

    /** Ruta relativa dentro del almacenamiento (nombre aleatorio, nunca el original). */
    @Column(nullable = false) private String storageKey;

    private String planType;

    private String scale;

    private String discipline;

    @JdbcTypeCode(LONG32VARCHAR) private String description;

    private int currentVersion = 1;
}
