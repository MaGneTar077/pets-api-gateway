package com.MyAnimaLog.api_gateway.filter;

import com.MyAnimaLog.api_gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            log.debug("JWT Filter processing: {}", path);

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("Missing or invalid Authorization header for path: {}", path);
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(BEARER_PREFIX.length()).trim();

            Optional<Claims> claimsOpt = jwtUtil.parseClaims(token);
            if (claimsOpt.isEmpty()) {
                log.warn("Invalid or expired token for path: {}", path);
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }

            Claims claims = claimsOpt.get();
            log.debug("JWT claim keys: {}", claims.keySet());

            String userId = resolveUserId(claims);
            String email = resolveEmail(claims);

            if (!StringUtils.hasText(userId) || !StringUtils.hasText(email)) {
                log.warn("Token without user identity for path: {}. Claims: {}", path, claims.keySet());
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Token does not contain user identity");
            }

            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove(IdentityHeaders.USER_ID);
                        headers.remove(IdentityHeaders.USER_EMAIL);
                        headers.set(IdentityHeaders.USER_ID, userId);
                        headers.set(IdentityHeaders.USER_EMAIL, email);
                    })
                    .build();

            log.debug("Authenticated user {} for path {}", userId, path);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private String resolveUserId(Claims claims) {
        Object userId = claims.get("userId");
        if (userId == null) {
            userId = claims.get("id");
        }
        if (userId != null) {
            return userId.toString();
        }
        String subject = claims.getSubject();
        return (subject != null && !subject.contains("@")) ? subject : null;
    }

    private String resolveEmail(Claims claims) {
        Object email = claims.get("email");
        String value = email != null ? email.toString() : claims.getSubject();
        if (value == null || !value.contains("@")) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\": \"" + message + "\", \"status\": " + status.value() + "}";
        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {}
}