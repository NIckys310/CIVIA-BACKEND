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

/** Perfil profesional del usuario. */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true) private User user;

    @JdbcTypeCode(LONG32VARCHAR) private String bio;

    @JdbcTypeCode(LONG32VARCHAR) private String company;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @JdbcTypeCode(LONG32VARCHAR) private String professionalId;

    @JdbcTypeCode(LONG32VARCHAR) private String city;

    @JdbcTypeCode(LONG32VARCHAR) private String country;

    @JdbcTypeCode(LONG32VARCHAR) private String website;
}
