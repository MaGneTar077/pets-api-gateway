package com.MyAnimaLog.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * End-to-end checks that don't require a live downstream: CORS preflight (handled by
 * CorsWebFilter before any route is even matched), and that the new Medical/Calendar
 * paths are now routed (401 from JwtAuthFilter) instead of 404 (no route at all).
 * Authorization-header forwarding and the DedupeResponseHeader wiring are covered at
 * the unit/route level in JwtAuthFilterTest and RouteConfigTest respectively.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final String ALLOWED_ORIGIN = "https://allowed.example.com";
    private static final String DISALLOWED_ORIGIN = "https://not-allowed.example.com";
    private static final String DEPLOYED_FRONTEND_ORIGIN = "https://project-ea0d4de8-cedf-4542-8fa.web.app";

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Trailing slash and stray spaces on purpose: must still normalize and match.
        registry.add("CORS_ALLOWED_ORIGINS", () -> " " + ALLOWED_ORIGIN + "/ , " + DEPLOYED_FRONTEND_ORIGIN + " ");
    }

    @Test
    void preflightFromAllowedOrigin_getsThatOriginBack_noTokenNeeded() {
        webTestClient.options().uri("/api/user/register")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void preflightFromDeployedFrontend_withRealRequestHeaders_passes() {
        webTestClient.options().uri("/api/user/register")
                .header(HttpHeaders.ORIGIN, DEPLOYED_FRONTEND_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "authorization,content-type,x-requested-with,cache-control")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEPLOYED_FRONTEND_ORIGIN);
    }

    @Test
    void preflightFromDisallowedOrigin_isRejected() {
        webTestClient.options().uri("/api/user/register")
                .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void medicalNewPaths_withoutToken_return401NotFound() {
        webTestClient.get().uri("/api/allergies/123").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/dewormings/123").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/documents/123").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void calendarNewPaths_withoutToken_return401() {
        webTestClient.get().uri("/api/veterinaries/123/services").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/me/appointments").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void internalPathsNeverReachAnyDestination() {
        // Root-level, service-to-service-only paths: no Path predicate matches them.
        webTestClient.get().uri("/internal/jobs/appointments/reminders").exchange().expectStatus().isNotFound();
        webTestClient.get().uri("/internal/verification/analyze").exchange().expectStatus().isNotFound();
    }
}
