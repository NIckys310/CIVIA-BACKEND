package com.civia;

import com.civia.ai.CiviaAi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class CiviaApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(CiviaApplication.class, args);
    }

    /** Capa de IA (repositorio CIVIA-IA). Usa Claude si hay ANTHROPIC_API_KEY; si no, el motor local. */
    @Bean
    CiviaAi civiaAi(@Value("${civia.ai.anthropic-api-key:}") String apiKey, @Value("${civia.ai.model:}") String model) {
        return CiviaAi.create(apiKey, model);
    }
}
