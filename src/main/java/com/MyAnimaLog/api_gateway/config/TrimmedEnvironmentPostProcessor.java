package com.MyAnimaLog.api_gateway.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Trims CALENDAR_SERVICE_URL before Spring resolves the "${CALENDAR_SERVICE_URL:...}"
 * placeholder in application.yaml, so a stray trailing space from copy-pasting the Cloud
 * Run URL doesn't silently turn it into a bad host.
 */
public class TrimmedEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY = "CALENDAR_SERVICE_URL";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String value = environment.getProperty(PROPERTY);
        if (value != null && !value.equals(value.trim())) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("trimmedServiceUrls", Map.of(PROPERTY, value.trim())));
        }
    }
}
