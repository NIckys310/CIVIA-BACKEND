package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Carpeta de archivos del proyecto. */
@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor
public class Folder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) private Folder parent;

    @Column(columnDefinition = "text", nullable = false) private String name;
}
