package com.swen3.swen3rest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Web UI, which runs on a different origin (host:port) than this REST server,
 * to call the API from the browser.
 *
 * Allowed origins come from the "app.cors.allowed-origins" property (comma-separated),
 * so they can be changed per environment, e.g. via the APP_CORS_ALLOWED_ORIGINS env var
 * in docker-compose.yml, without rebuilding.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);                   // browsers may cache the preflight response for 1h
    }
}
