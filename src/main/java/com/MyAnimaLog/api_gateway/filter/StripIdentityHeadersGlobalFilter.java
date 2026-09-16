package com.MyAnimaLog.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class StripIdentityHeadersGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();

        if (!headers.containsKey(IdentityHeaders.USER_ID)
                && !headers.containsKey(IdentityHeaders.USER_EMAIL)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest cleaned = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(IdentityHeaders.USER_ID);
                    h.remove(IdentityHeaders.USER_EMAIL);
                })
                .build();

        return chain.filter(exchange.mutate().request(cleaned).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
