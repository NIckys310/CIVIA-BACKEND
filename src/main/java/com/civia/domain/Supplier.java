package com.civia.domain;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Proveedor. Contacto cifrado en reposo. */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Organization organization;

    @Column(columnDefinition = "text", nullable = false) private String name;

    @Column(columnDefinition = "text") private String contactName;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @Column(columnDefinition = "text") private String phone;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @Column(columnDefinition = "text") private String email;

    @Column(columnDefinition = "text") private String website;
}
