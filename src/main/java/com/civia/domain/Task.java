package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tarea del proyecto. */
@Entity
@Table(name = "tasks", indexes = {@Index(name = "ix_tasks_project", columnList = "project_id")})
@Getter
@Setter
@NoArgsConstructor
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) private ProjectPhase phase;

    @Column(columnDefinition = "text", nullable = false) private String title;

    @Column(columnDefinition = "text") private String description;

    @Enumerated(EnumType.STRING) private Enums.TaskStatus status = Enums.TaskStatus.TODO;

    @Enumerated(EnumType.STRING) private Enums.Priority priority = Enums.Priority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY) private User assignee;

    @ManyToOne(fetch = FetchType.LAZY) private User createdBy;

    private LocalDate dueDate;

    private Instant completedAt;
}
