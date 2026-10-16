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

/** Invitación a un proyecto. */
@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor
public class Invitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String email;

    @Enumerated(EnumType.STRING) private Enums.ProjectRole role = Enums.ProjectRole.VIEWER;

    @Column(nullable = false, unique = true, length = 64) private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY) private User invitedBy;

    private Instant expiresAt;

    private Instant acceptedAt;
}
