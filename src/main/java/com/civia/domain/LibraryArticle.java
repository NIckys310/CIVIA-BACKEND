package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(columnDefinition = "text", nullable = false) private String title;

    @Column(columnDefinition = "text") private String summary;

    @Column(columnDefinition = "text", nullable = false) private String body;

    @Column(columnDefinition = "text") private String tagsCsv;
}
