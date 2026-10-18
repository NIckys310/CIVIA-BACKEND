package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(columnDefinition = "text", nullable = false) private String name;

    @Column(columnDefinition = "text") private String description;

    @Column(columnDefinition = "text") private String type;

    @Enumerated(EnumType.STRING) private Enums.ProjectStatus status = Enums.ProjectStatus.DESIGN;

    @Column(columnDefinition = "text") private String phase;

    @Column(columnDefinition = "text") private String location;

    @Column(columnDefinition = "text") private String address;

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
