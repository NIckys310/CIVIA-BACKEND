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

/** Versión anterior o actual de un archivo. */
@Entity
@Table(name = "document_versions")
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Document document;

    private int version;

    @Column(nullable = false) private String storageKey;

    private long sizeBytes;

    @Column(length = 64) private String sha256;

    @ManyToOne(fetch = FetchType.LAZY) private User uploadedBy;

    @JdbcTypeCode(LONG32VARCHAR) private String note;
}
