package com.MyAnimaLog.api_gateway.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    private static final ObjectMapper HEADER_MAPPER = new ObjectMapper();

    private final Key legacyHmacKey;
    private final ReactiveJwtDecoder rsaDecoder;
    private final boolean acceptLegacy;

    public JwtUtil(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.jwks-uri}") String jwksUri,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience,
            @Value("${jwt.accept-legacy}") boolean acceptLegacy) {
        this.acceptLegacy = acceptLegacy;

        if (acceptLegacy) {
            if (!StringUtils.hasText(secret)) {
                throw new IllegalStateException(
                        "jwt.secret (JWT_SECRET) is required while jwt.accept-legacy=true");
            }
            this.legacyHmacKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } else {
            this.legacyHmacKey = null;
        }

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        this.rsaDecoder = decoder;

        log.info("JWT validation configured: jwksUri={}, issuer={}, audience={}, acceptLegacy={}",
                jwksUri, issuer, audience, acceptLegacy);
    }

    /**
     * Validates a token, accepting RS256 (JWKS, keyed by {@code kid}) and, while
     * {@code jwt.accept-legacy=true}, HS256 tokens signed with the shared secret.
     * On failure the Mono errors (it never completes empty) so the caller can log and
     * report the real cause; it never logs itself to avoid duplicate/split log lines.
     * TODO: drop the legacy HS256 branch once no client holds a pre-RS256 token.
     */
    public Mono<Map<String, Object>> validate(String token) {
        if (hasKid(token)) {
            return Mono.defer(() -> rsaDecoder.decode(token)).map(Jwt::getClaims);
        }

        if (!acceptLegacy) {
            return Mono.error(new org.springframework.security.oauth2.jwt.JwtException(
                    "Legacy HS256 tokens are disabled (jwt.accept-legacy=false)"));
        }

        return Mono.fromCallable(() -> parseLegacyClaims(token));
    }

    private Map<String, Object> parseLegacyClaims(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(legacyHmacKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        if (claims.getExpiration() == null) {
            throw new io.jsonwebtoken.JwtException("Legacy token has no expiration claim");
        }

        return claims;
    }

    private boolean hasKid(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode header = HEADER_MAPPER.readTree(headerJson);
            return header.has("kid") && StringUtils.hasText(header.get("kid").asText());
        } catch (Exception e) {
            return false;
        }
    }
}
