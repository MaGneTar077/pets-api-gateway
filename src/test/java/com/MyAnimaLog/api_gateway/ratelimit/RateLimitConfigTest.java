package com.MyAnimaLog.api_gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    @Test
    void extractsIdClaimFromRs256ShapedToken() {
        String header = base64Url("{\"alg\":\"RS256\",\"kid\":\"prod-2026-10-key-1\"}");
        String payload = base64Url(
                "{\"id\":\"user-123\",\"iss\":\"myanimalog-user-service\",\"aud\":\"myanimalog-api\"}");
        String token = header + "." + payload + ".fake-signature";

        KeyResolver resolver = new RateLimitConfig().userKeyResolver();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/pets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("user-123");
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
