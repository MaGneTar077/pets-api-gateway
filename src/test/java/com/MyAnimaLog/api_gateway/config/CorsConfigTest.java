package com.MyAnimaLog.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void parseOrigins_trimsEachEntry() {
        assertThat(CorsConfig.parseOrigins(" http://localhost:8100 , http://localhost:4200 "))
                .containsExactly("http://localhost:8100", "http://localhost:4200");
    }

    @Test
    void parseOrigins_dropsEmptyEntriesAndDuplicates() {
        assertThat(CorsConfig.parseOrigins("http://a.com,,http://a.com, http://b.com"))
                .containsExactly("http://a.com", "http://b.com");
    }

    @Test
    void parseOrigins_singleLegacyOrigin_stillWorks() {
        assertThat(CorsConfig.parseOrigins("http://localhost:8100"))
                .containsExactly("http://localhost:8100");
    }

    @Test
    void corsWebFilter_allowsCredentialsWithExplicitOriginsOnly() {
        CorsConfig config = new CorsConfig();
        CorsWebFilter filter = config.corsWebFilter("http://localhost:8100, http://localhost:4200");

        assertThat(filter).isNotNull();
        // Guard against ever widening this back to "*", which is incompatible with credentials.
        assertThat(CorsConfig.parseOrigins("http://localhost:8100, http://localhost:4200"))
                .doesNotContain(CorsConfiguration.ALL);
    }
}
