package com.MyAnimaLog.api_gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private static final int MAX_CAUSE_DEPTH = 10;

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        ServerHttpRequest request = exchange.getRequest();
        HttpStatus status = resolveStatus(ex);
        String message = resolveMessage(ex, status);
        String path = request.getPath().value();

        if (status.is5xxServerError()) {
            log.error("Gateway error [{}] {} {} → {}",
                    status.value(), request.getMethod(), path, ex.getMessage(), ex);
        } else {
            log.warn("Gateway client error [{}] {} {} → {}",
                    status.value(), request.getMethod(), path, message);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = response.bufferFactory().wrap(toJson(body));
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
        }

        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof ConnectException || current instanceof UnknownHostException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (current instanceof TimeoutException || current instanceof ReadTimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            current = current.getCause();
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(Throwable ex, HttpStatus status) {
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return switch (status) {
            case SERVICE_UNAVAILABLE -> "Service is currently unavailable";
            case GATEWAY_TIMEOUT -> "Service timeout";
            case INTERNAL_SERVER_ERROR -> "Internal server error";
            default -> status.getReasonPhrase();
        };
    }

    private byte[] toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            log.error("Error serializing error response", e);
            return "{\"status\":500,\"error\":\"Internal Server Error\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}