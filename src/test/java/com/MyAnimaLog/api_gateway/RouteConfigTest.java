package com.MyAnimaLog.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RouteConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void veterinaryRoute_forwardsPathUnchanged_noRewrite() {
        Route route = findRoute("veterinary-service");
        assertThat(hasRewritePathFilter(route))
                .as("veterinary-service must NOT strip /api: the downstream service expects it")
                .isFalse();
    }

    @Test
    void userAuthRoute_stripsApiPrefix() {
        Route route = findRoute("user-auth");
        assertThat(hasRewritePathFilter(route))
                .as("user-auth (and the rest of User/Pets) must still strip /api")
                .isTrue();
    }

    private Route findRoute(String id) {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        return routes.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Route not found: " + id));
    }

    private boolean hasRewritePathFilter(Route route) {
        return route.getFilters().stream()
                .anyMatch(f -> f.toString().contains("RewritePath"));
    }
}
