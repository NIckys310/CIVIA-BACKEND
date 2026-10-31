package com.civia.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sirve la interfaz web (repositorio CIVIA-FRONTEND) desde el mismo origen que la API: sin CORS,
 * cookies SameSite=Strict y una sola conexión HTTP/2 para todo, lo que acelera la carga.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(WebConfig.class);
    private final Path webDir;

    public WebConfig(@Value("${civia.web.dir}") String webDir) {
        this.webDir = Path.of(webDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!Files.isDirectory(webDir)) {
            LOG.warn("No se encontró la interfaz web en {} (define CIVIA_WEB_DIR). Solo se sirve la API.", webDir);
            return;
        }
        String location = webDir.toUri().toString();
        // Librerías e imágenes: caché larga. Código de la app: revalidación (304) para que las
        // actualizaciones lleguen al instante; el service worker la sirve sin esperar a la red.
        registry.addResourceHandler("/assets/**", "/vendor/**")
                .addResourceLocations(location + "assets/", location + "vendor/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
        registry.addResourceHandler("/css/**", "/js/**")
                .addResourceLocations(location + "css/", location + "js/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/index.html", "/sw.js", "/manifest.webmanifest", "/favicon.ico")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noCache());
        LOG.info("Interfaz web servida desde {}", webDir);
    }

    /** ETag y revalidación (304) para la biblioteca y el glosario. */
    @org.springframework.context.annotation.Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<RevalidationCacheFilter> revalidationCache() {
        var bean = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new RevalidationCacheFilter());
        bean.addUrlPatterns("/api/v1/library/categories", "/api/v1/library/articles", "/api/v1/library/articles/*",
                "/api/v1/library/terms", "/api/v1/library/stats", "/api/v1/library/featured");
        bean.setName("revalidationCache");
        return bean;
    }

    /** Tipos MIME que el servidor embebido no conoce por defecto. */
    @org.springframework.context.annotation.Bean
    org.springframework.boot.web.server.WebServerFactoryCustomizer<org.springframework.boot.web.server.ConfigurableWebServerFactory> mimeTypes() {
        return factory -> {
            if (factory instanceof org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory servlet) {
                org.springframework.boot.web.server.MimeMappings mappings = new org.springframework.boot.web.server.MimeMappings(org.springframework.boot.web.server.MimeMappings.DEFAULT);
                mappings.add("webmanifest", "application/manifest+json");
                mappings.add("glb", "model/gltf-binary");
                mappings.add("mjs", "text/javascript");
                servlet.setMimeMappings(mappings);
            }
        };
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (Files.isDirectory(webDir)) {
            registry.addViewController("/").setViewName("forward:/index.html");
        }
    }
}
