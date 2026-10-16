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

/** Proveedor. Contacto cifrado en reposo. */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Organization organization;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String name;

    @JdbcTypeCode(LONG32VARCHAR) private String contactName;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @JdbcTypeCode(LONG32VARCHAR) private String phone;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @JdbcTypeCode(LONG32VARCHAR) private String email;

    @JdbcTypeCode(LONG32VARCHAR) private String website;
}
