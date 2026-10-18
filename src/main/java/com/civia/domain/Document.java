package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(columnDefinition = "text", nullable = false) private String name;

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

    @Column(columnDefinition = "text") private String description;

    private int currentVersion = 1;
}
