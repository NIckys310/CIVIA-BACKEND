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

/** Miembro de un proyecto con su rol. */
@Entity
@Table(name = "project_members", indexes = {@Index(name = "ix_pm_user", columnList = "user_id"), @Index(name = "ix_pm_project", columnList = "project_id")})
@Getter
@Setter
@NoArgsConstructor
public class ProjectMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User user;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private Enums.ProjectRole role = Enums.ProjectRole.VIEWER;
}
