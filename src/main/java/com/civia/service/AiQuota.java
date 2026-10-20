package com.civia.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cuota de preguntas al Copilot por usuario (ventana fija de un minuto). Protege el costo de la
 * API de Claude frente a bucles o abusos, aparte del límite general por IP.
 */
@Component
public class AiQuota {

    private record Window(long minute, int count) {}

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final int perMinute;

    public AiQuota(@Value("${civia.ai.requests-per-minute:20}") int perMinute) {
        this.perMinute = perMinute;
    }

    /** Cuenta una pregunta; si se supera la cuota responde 429. */
    public void acquire(UUID userId) {
        long minute = System.currentTimeMillis() / 60_000;
        Window w = windows.compute(userId, (k, old) -> old == null || old.minute() != minute
                ? new Window(minute, 1) : new Window(minute, old.count() + 1));
        if (windows.size() > 50_000) windows.values().removeIf(x -> x.minute() < minute);
        if (w.count() > perMinute) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Hiciste muchas preguntas en poco tiempo. Espera un minuto e intenta de nuevo.");
        }
    }
}
