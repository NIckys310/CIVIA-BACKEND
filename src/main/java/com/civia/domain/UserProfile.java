package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Perfil profesional del usuario. */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true) private User user;

    @Column(columnDefinition = "text") private String bio;

    @Column(columnDefinition = "text") private String company;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @Column(columnDefinition = "text") private String professionalId;

    @Column(columnDefinition = "text") private String city;

    @Column(columnDefinition = "text") private String country;

    @Column(columnDefinition = "text") private String website;
}
