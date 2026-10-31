package com.civia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Respuestas de solo lectura que cambian poco (biblioteca, glosario, calculadoras): el navegador
 * puede guardarlas pero debe revalidarlas siempre ({@code private, no-cache}); si nada cambió la
 * API responde 304 sin cuerpo gracias al ETag. Así se ahorra la descarga sin servir datos viejos.
 */
public class RevalidationCacheFilter extends ShallowEtagHeaderFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Se fija antes que Spring Security, que solo pone "no-store" si no hay Cache-Control.
        if (response.getHeader("Cache-Control") == null) response.setHeader("Cache-Control", "private, no-cache");
        super.doFilterInternal(request, response, chain);
    }
}
