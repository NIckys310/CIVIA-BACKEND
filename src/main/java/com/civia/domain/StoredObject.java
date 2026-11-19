package com.civia.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Archivo subido guardado dentro de la base de datos (bytea en PostgreSQL). Se usa cuando no hay un
 * almacenamiento externo S3: así los planos y fotos sobreviven a los reinicios del servidor.
 */
@Entity
@Table(name = "stored_objects")
@Getter
@Setter
@NoArgsConstructor
public class StoredObject {

    @Id
    @Column(name = "object_key", length = 200)
    private String key;

    @Column(name = "size_bytes", nullable = false)
    private long size;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
