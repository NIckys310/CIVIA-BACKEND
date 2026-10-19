package com.civia;

import static org.assertj.core.api.Assertions.assertThat;

import com.civia.config.DatabaseUrlPostProcessor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DatabaseUrlTest {
    @Test
    void renderUrlBecomesJdbc() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("t", Map.of("DATABASE_URL", "postgresql://civia:p%40ss@dpg-abc-a:5432/civia")));
        new DatabaseUrlPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://dpg-abc-a:5432/civia");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("civia");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p@ss");
    }

    @Test
    void neonUrlKeepsSslAndDropsLibpqOnlyParameters() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("t", Map.of("DATABASE_URL",
                "postgresql://civia_owner:s3cr%2Bto@ep-x-123.us-west-2.aws.neon.tech/civia?sslmode=require&channel_binding=require")));
        new DatabaseUrlPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://ep-x-123.us-west-2.aws.neon.tech:5432/civia?sslmode=require");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("civia_owner");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("s3cr+to");
    }
}
