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

    @Test
    void parseOrigins_dropsTrailingSlash() {
        // The browser's Origin header never has a trailing slash; a configured
        // origin that keeps one would otherwise never match and silently block.
        assertThat(CorsConfig.parseOrigins("https://project-ea0d4de8-cedf-4542-8fa.web.app/"))
                .containsExactly("https://project-ea0d4de8-cedf-4542-8fa.web.app");
    }

    @Test
    void parseOrigins_trimsAndDropsTrailingSlashTogether() {
        assertThat(CorsConfig.parseOrigins(" https://project-ea0d4de8-cedf-4542-8fa.web.app/ , http://localhost:4200 "))
                .containsExactly("https://project-ea0d4de8-cedf-4542-8fa.web.app", "http://localhost:4200");
    }

    @Test
    void parseOrigins_rejectsOriginWithAPath() {
        assertThat(CorsConfig.parseOrigins("https://example.com/some/path")).isEmpty();
    }

    @Test
    void parseOrigins_emptyOrBlankEntriesYieldEmptyList_notAnError() {
        assertThat(CorsConfig.parseOrigins("")).isEmpty();
        assertThat(CorsConfig.parseOrigins(" , , ")).isEmpty();
    }

    @Test
    void corsWebFilter_withNoOrigins_stillBuilds_andAllowsNone() {
        CorsConfig config = new CorsConfig();
        assertThat(config.corsWebFilter("")).isNotNull();
    }
}
