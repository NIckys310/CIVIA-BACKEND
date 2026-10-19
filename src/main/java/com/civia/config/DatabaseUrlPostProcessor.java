package com.civia.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Acepta {@code DATABASE_URL} en el formato que entregan Render, Neon o Heroku
 * ({@code postgresql://usuario:clave@host:5432/db}) y lo traduce a JDBC con usuario y clave aparte.
 * Así la base se conecta sin copiar credenciales a mano.
 */
public class DatabaseUrlPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String raw = env.getProperty("DATABASE_URL");
        if (raw == null || !(raw.startsWith("postgres://") || raw.startsWith("postgresql://"))) return;
        URI uri = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        String info = uri.getRawUserInfo();
        if (info != null) {
            String[] parts = info.split(":", 2);
            props.put("spring.datasource.username", URLDecoder.decode(parts[0], StandardCharsets.UTF_8));
            if (parts.length > 1) props.put("spring.datasource.password", URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        env.getPropertySources().addFirst(new MapPropertySource("civia-database-url", props));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
