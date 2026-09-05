package com.razorpay.recovery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;    /**
     * Global CORS configuration.
     * Reads the FRONTEND_ORIGIN env var and defaults to {@code *} for local development.
     *
     * ⚠ SECURITY WARNING (production): the {@code *} default combined with
     * allowCredentials(true) is safe for local dev only. Before any production
     * deploy (Railway/Render/Vercel) you MUST set FRONTEND_ORIGIN to the exact
     * deployed frontend URL (e.g. https://your-app.vercel.app) — see DEPLOY.md
     * "Post-Deploy: Lock Down CORS". With credentials allowed, the wildcard would
     * otherwise let any origin read authenticated responses.
     */
@Configuration
public class WebConfig {

    @Value("${FRONTEND_ORIGIN:*}")
    private String frontendOrigin;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        if ("*".equals(frontendOrigin)) {
            config.addAllowedOriginPattern("*");
        } else {
            config.addAllowedOrigin(frontendOrigin);
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
