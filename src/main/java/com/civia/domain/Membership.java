package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pertenencia de un usuario a una organización. */
@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private User user;

    private String role = "MEMBER";
}
