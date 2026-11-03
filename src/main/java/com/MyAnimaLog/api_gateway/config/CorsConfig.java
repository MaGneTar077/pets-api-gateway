package com.MyAnimaLog.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the global CORS policy from a comma-separated origin list ({@code frontend.urls}),
 * trimming each entry so a stray space from copy-pasting an origin doesn't silently break it.
 * Replaces the Spring Cloud Gateway YAML {@code globalcors} block, which only supported a
 * single origin.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(@Value("${frontend.urls}") String frontendUrls) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseOrigins(frontendUrls));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }

    static List<String> parseOrigins(String frontendUrls) {
        return Arrays.stream(frontendUrls.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
