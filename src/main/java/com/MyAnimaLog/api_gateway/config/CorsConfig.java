package com.MyAnimaLog.api_gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the single, global CORS policy from a comma-separated origin list
 * ({@code cors.allowed-origins}), normalizing each entry (trim, drop a trailing
 * slash) so a stray space or copy-pasted trailing "/" doesn't silently make an
 * origin never match the browser's exact "Origin" header. This is the only CORS
 * configuration in the gateway (no YAML {@code globalcors}, no duplicate setup).
 * We use Bearer tokens, not cookies, so credentials are never allowed and the
 * wildcard origin is never accepted even if someone puts "*" in the env var.
 */
@Slf4j
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(@Value("${cors.allowed-origins}") String allowedOrigins) {
        List<String> origins = parseOrigins(allowedOrigins);
        log.info("CORS allowed origins: {}", origins);

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }

    static List<String> parseOrigins(String allowedOrigins) {
        return Arrays.stream(allowedOrigins.split(","))
                .map(CorsConfig::normalize)
                .filter(StringUtils::hasText)
                .filter(origin -> !CorsConfiguration.ALL.equals(origin))
                .filter(CorsConfig::isSchemeHostOnly)
                .distinct()
                .toList();
    }

    private static String normalize(String origin) {
        String trimmed = origin.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** An Origin header is always just scheme://host[:port], never a path. Reject anything else. */
    private static boolean isSchemeHostOnly(String origin) {
        try {
            URI uri = URI.create(origin);
            return uri.getScheme() != null && uri.getHost() != null
                    && (uri.getPath() == null || uri.getPath().isEmpty());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
