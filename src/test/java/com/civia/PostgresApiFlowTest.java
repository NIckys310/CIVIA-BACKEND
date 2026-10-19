package com.civia;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** El mismo flujo completo de {@link ApiFlowTest}, pero sobre PostgreSQL real (como en producción). */
class PostgresApiFlowTest extends ApiFlowTest {

    private static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) {
        // Formato de Render/Neon: lo traduce DatabaseUrlPostProcessor.
        r.add("DATABASE_URL", () -> "postgresql://postgres:postgres@localhost:" + PG.getPort() + "/postgres");
        r.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + PG.getPort() + "/postgres");
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
