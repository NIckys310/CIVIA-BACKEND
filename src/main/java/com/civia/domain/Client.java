package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cliente del estudio. Contacto cifrado en reposo. */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Organization organization;

    @Column(columnDefinition = "text", nullable = false) private String name;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @Column(columnDefinition = "text") private String email;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @Column(columnDefinition = "text") private String phone;

    @Column(columnDefinition = "text") private String company;

    @Column(columnDefinition = "text") private String notes;
}
