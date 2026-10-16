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

/** Cliente del estudio. Contacto cifrado en reposo. */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY) private Organization organization;

    @JdbcTypeCode(LONG32VARCHAR) @Column(nullable = false) private String name;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @JdbcTypeCode(LONG32VARCHAR) private String email;

    @Convert(converter = com.civia.security.EncryptedStringConverter.class) @JdbcTypeCode(LONG32VARCHAR) private String phone;

    @JdbcTypeCode(LONG32VARCHAR) private String company;

    @JdbcTypeCode(LONG32VARCHAR) private String notes;
}
