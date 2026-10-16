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

/** Proyecto arquitectónico. */
@Entity
@Table(name = "projects", indexes = {@Index(name = "ix_projects_owner", columnList = "owner_id")})
@Getter
@Setter
@NoArgsConstructor
public class Project extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User owner;

    @ManyToOne(fetch = FetchType.LAZY) private Client client;

    private String code;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String name;

    @JdbcTypeCode(LONG32VARCHAR) private String description;

    @JdbcTypeCode(LONG32VARCHAR) private String type;

    @Enumerated(EnumType.STRING) private Enums.ProjectStatus status = Enums.ProjectStatus.DESIGN;

    @JdbcTypeCode(LONG32VARCHAR) private String phase;

    @JdbcTypeCode(LONG32VARCHAR) private String location;

    @JdbcTypeCode(LONG32VARCHAR) private String address;

    private Double latitude;

    private Double longitude;

    private Double lotArea;

    private Double builtArea;

    private Integer floors;

    @Column(precision = 19, scale = 2) private BigDecimal budgetEstimate;

    private String currency = "COP";

    private LocalDate startDate;

    private LocalDate dueDate;

    private String coverColor;

    private boolean archived;

    @ManyToMany

    @JoinTable(name = "project_tags", joinColumns = @JoinColumn(name = "project_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))

    private java.util.Set<Tag> tags = new java.util.HashSet<>();
}
