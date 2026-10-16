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

/** Artículo de la biblioteca creado por la organización. */
@Entity
@Table(name = "library_articles")
@Getter
@Setter
@NoArgsConstructor
public class LibraryArticle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY) private User author;

    @Column(nullable = false, unique = true) private String slug;

    @Column(nullable = false) private String category;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String title;

    @JdbcTypeCode(LONG32VARCHAR) private String summary;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String body;

    @JdbcTypeCode(LONG32VARCHAR) private String tagsCsv;
}
