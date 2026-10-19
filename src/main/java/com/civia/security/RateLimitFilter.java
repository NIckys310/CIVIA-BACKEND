package com.civia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de peticiones por IP con ventana fija de un minuto. Más estricto en CIVIA Auth para
 * frenar ataques de fuerza bruta y relleno de credenciales.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final int authPerMinute;
    private final int apiPerMinute;

    private record Window(long minute, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int authPerMinute, int apiPerMinute) {
        this.authPerMinute = authPerMinute;
        this.apiPerMinute = apiPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean auth = path.startsWith("/api/v1/auth/") && "POST".equals(req.getMethod());
        String key = (auth ? "auth:" : "api:") + req.getRemoteAddr();
        int limit = auth ? authPerMinute : apiPerMinute;
        long minute = System.currentTimeMillis() / 60_000;
        Window w = windows.compute(key, (k, old) -> old == null || old.minute() != minute
                ? new Window(minute, 1) : new Window(minute, old.count() + 1));
        if (windows.size() > 50_000) windows.values().removeIf(x -> x.minute() < minute);
        if (w.count() > limit) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Demasiadas solicitudes. Intenta de nuevo en un minuto.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
