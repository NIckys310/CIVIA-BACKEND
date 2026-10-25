package com.civia.web;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.swagger.v3.oas.annotations.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado de la caché del servidor para el panel «Almacenamiento y caché» del perfil. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /** Qué guarda cada caché y cuándo se renueva (texto para la interfaz). */
    private static final Map<String, String[]> DESCRIPTIONS = Map.of(
            LibraryController.LIBRARY_CACHE, new String[] {"Biblioteca y glosario", "al crear, editar o borrar un artículo"},
            "massing", new String[] {"Volumetrías", "cada consulta distinta se calcula de nuevo"});

    private final CacheManager caches;

    public SystemController(CacheManager caches) {
        this.caches = caches;
    }

    public record CacheEntry(String name, String label, String renews, long size, long hits, long misses, double hitRate) {}

    public record CacheReport(boolean enabled, long hits, long misses, double hitRate, List<CacheEntry> caches) {}

    @Operation(summary = "Aciertos y tamaño de la caché del servidor")
    @GetMapping("/cache")
    public CacheReport cache() {
        List<CacheEntry> entries = new ArrayList<>();
        long hits = 0;
        long misses = 0;
        for (String name : caches.getCacheNames().stream().sorted().toList()) {
            if (!(caches.getCache(name) instanceof CaffeineCache cc)) continue;
            CacheStats s = cc.getNativeCache().stats();
            String[] d = DESCRIPTIONS.getOrDefault(name, new String[] {name, "al vencer"});
            entries.add(new CacheEntry(name, d[0], d[1], cc.getNativeCache().estimatedSize(), s.hitCount(), s.missCount(), round(s.hitRate())));
            hits += s.hitCount();
            misses += s.missCount();
        }
        long total = hits + misses;
        return new CacheReport(!entries.isEmpty(), hits, misses, total == 0 ? 0 : round((double) hits / total), entries);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
