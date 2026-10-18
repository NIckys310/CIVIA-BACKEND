package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Búsquedas en la biblioteca (para mejorar el contenido). */
@Entity
@Table(name = "library_search_log")
@Getter
@Setter
@NoArgsConstructor
public class LibrarySearchLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private User user;

    @Column(columnDefinition = "text") private String query;

    private int resultCount;
}
