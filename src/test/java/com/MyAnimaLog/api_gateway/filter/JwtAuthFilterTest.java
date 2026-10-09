package com.MyAnimaLog.api_gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.MyAnimaLog.api_gateway.util.JwtUtil;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String KID = "test-key-1";
    private static final String ISSUER = "myanimalog-user-service";
    private static final String AUDIENCE = "myanimalog-api";
    private static final String LEGACY_SECRET = "test-legacy-secret-value-0123456789AB";

    private static HttpServer jwksServer;
    private static KeyPair signingKeyPair;

    @BeforeAll
    static void startFakeJwks() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKeyPair = generator.generateKeyPair();

        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) signingKeyPair.getPublic())
                .keyID(KID)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        String jwksJson = "{\"keys\":[" + jwk.toPublicJWK().toJSONString() + "]}";
        byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);

        jwksServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwksServer.start();
    }

    @AfterAll
    static void stopFakeJwks() {
        jwksServer.stop(0);
    }

    private String jwksUri() {
        return "http://localhost:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
    }

    private JwtUtil newJwtUtil(boolean acceptLegacy) {
        return new JwtUtil(acceptLegacy ? LEGACY_SECRET : "", jwksUri(), ISSUER, AUDIENCE, acceptLegacy);
    }

    private String rsaToken(KeyPair keyPair, String kid, String issuer, String audience, Instant expiresAt,
                             String userId, String email) throws Exception {
        return rsaToken(keyPair, kid, issuer, audience, Instant.now(), expiresAt, userId, email);
    }

    private String rsaToken(KeyPair keyPair, String kid, String issuer, String audience, Instant issuedAt,
                             Instant expiresAt, String userId, String email) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(userId)
                .claim("id", userId)
                .claim("email", email)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    /** Builds a token with "aud" as a bare JSON string (not an array), as some issuers emit it. */
    private String rsaTokenWithPlainStringAudience(KeyPair keyPair, String kid, String issuer, String audience,
                                                    Instant expiresAt, String userId, String email) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .claim("aud", audience)
                .subject(userId)
                .claim("id", userId)
                .claim("email", email)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    private String legacyToken(String secret, String userId, String email, Instant expiresAt) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", email)
                .setExpiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private ServerWebExchange exchangeWithBearerToken(String token) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pets/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        return MockServerWebExchange.from(request);
    }

    private GatewayFilter filterUnder(JwtUtil jwtUtil) {
        return new JwtAuthFilter(jwtUtil).apply(new JwtAuthFilter.Config());
    }

    private void assertRejected(GatewayFilter filter, ServerWebExchange exchange) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        filter.filter(exchange, chain).block();
        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validRsaToken_passesAndSetsIdentityHeaders() throws Exception {
        GatewayFilter filter = filterUnder(newJwtUtil(true));
        String token = rsaToken(signingKeyPair, KID, ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), "user-123", "User@Example.com");
        ServerWebExchange exchange = exchangeWithBearerToken(token);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders mutated = captor.getValue().getRequest().getHeaders();
        assertThat(mutated.getFirst(IdentityHeaders.USER_ID)).isEqualTo("user-123");
        assertThat(mutated.getFirst(IdentityHeaders.USER_EMAIL)).isEqualTo("user@example.com");
    }

    @Test
    void rsaToken_wrongIssuer_isRejected() throws Exception {
        GatewayFilter filter = filterUnder(newJwtUtil(true));
        String token = rsaToken(signingKeyPair, KID, "someone-else", AUDIENCE,
                Instant.now().plusSeconds(300), "user-123", "user@example.com");
        assertRejected(filter, exchangeWithBearerToken(token));
    }

    @Test
    void rsaToken_audienceAsPlainString_isAccepted() throws Exception {
        GatewayFilter filter = filterUnder(newJwtUtil(true));
        String token = rsaTokenWithPlainStringAudience(signingKeyPair, KID, ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), "user-123", "user@example.com");
        ServerWebExchange exchange = exchangeWithBearerToken(token);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void rsaToken_wrongAudience_isRejected() throws Exception {
        GatewayFilter filter = filterUnder(newJwtUtil(true));
        String token = rsaToken(signingKeyPair, KID, ISSUER, "someone-elses-api",
                Instant.now().plusSeconds(300), "user-123", "user@example.com");
        assertRejected(filter, exchangeWithBearerToken(token));
    }

    @Test
    void rsaToken_expired_isRejected() throws Exception {
        GatewayFilter filter = filterUnder(newJwtUtil(true));
        String token = rsaToken(signingKeyPair, KID, ISSUER, AUDIENCE,
                Instant.now().minusSeconds(1200), Instant.now().minusSeconds(600),
                "user-123", "user@example.com");
        assertRejected(filter, exchangeWithBearerToken(token));
    }

    @Test
    void rsaToken_badSignature_isRejected() throws Exception {
        GatewayFilter filter = filterUnder(newJwtUtil(true));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair attackerKeyPair = generator.generateKeyPair();

        // Signed with a key never published in the JWKS, but still claims the real kid.
        String token = rsaToken(attackerKeyPair, KID, ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), "user-123", "user@example.com");
        assertRejected(filter, exchangeWithBearerToken(token));
    }

    @Test
    void legacyHs256Token_acceptedWhenLegacyEnabled() {
        GatewayFilter filter = filterUnder(newJwtUtil(true));
        String token = legacyToken(LEGACY_SECRET, "legacy-user-1", "legacy@example.com",
                Instant.now().plusSeconds(300));
        ServerWebExchange exchange = exchangeWithBearerToken(token);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst(IdentityHeaders.USER_ID))
                .isEqualTo("legacy-user-1");
    }

    @Test
    void legacyHs256Token_rejectedWhenLegacyDisabled() {
        GatewayFilter filter = filterUnder(newJwtUtil(false));
        String token = legacyToken(LEGACY_SECRET, "legacy-user-1", "legacy@example.com",
                Instant.now().plusSeconds(300));
        assertRejected(filter, exchangeWithBearerToken(token));
    }

    @Test
    void missingAuthorizationHeader_isRejected() {
        GatewayFilter filter = filterUnder(newJwtUtil(true));
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/pets/123").build());
        assertRejected(filter, exchange);
    }

    @Test
    void unreachableJwks_isRejectedWithNetworkCause() throws Exception {
        // Port 1 is a privileged port with nothing listening, so the connection fails fast.
        JwtUtil jwtUtil = new JwtUtil(LEGACY_SECRET, "http://localhost:1/.well-known/jwks.json",
                ISSUER, AUDIENCE, true);
        GatewayFilter filter = filterUnder(jwtUtil);
        String token = rsaToken(signingKeyPair, KID, ISSUER, AUDIENCE,
                Instant.now().plusSeconds(300), "user-123", "user@example.com");
        assertRejected(filter, exchangeWithBearerToken(token));
    }

    @Test
    void rejectedToken_logsCauseWithoutLeakingTheToken() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            GatewayFilter filter = filterUnder(newJwtUtil(true));
            String token = rsaToken(signingKeyPair, KID, "someone-else", AUDIENCE,
                    Instant.now().plusSeconds(300), "user-123", "user@example.com");
            assertRejected(filter, exchangeWithBearerToken(token));

            java.util.List<String> causeLines = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("Cause:"))
                    .toList();

            // Exactly one WARN line for this request: no duplicate/split logging.
            assertThat(causeLines).hasSize(1);
            assertThat(causeLines.get(0)).contains("JwtValidationException");
            assertThat(causeLines.get(0)).doesNotContain(token);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
