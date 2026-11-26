package com.MyAnimaLog.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** CORS_ALLOWED_ORIGINS="" (or only blank entries) must not break startup, and must allow no origin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsEmptyOriginsIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("CORS_ALLOWED_ORIGINS", () -> " , , ");
    }

    @Test
    void contextStartsFine_andPreflightFromAnyOriginIsRejected() {
        webTestClient.options().uri("/api/user/register")
                .header(HttpHeaders.ORIGIN, "https://anything.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }
}
