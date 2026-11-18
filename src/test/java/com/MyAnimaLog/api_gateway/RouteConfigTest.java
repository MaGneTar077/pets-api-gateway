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

    @Test
    void iaRoute_forwardsPathUnchanged_noRewrite() {
        Route route = findRoute("ia-service");
        assertThat(hasRewritePathFilter(route))
                .as("ia-service must NOT strip /api: the downstream service expects it")
                .isFalse();
    }

    @Test
    void noDedicatedRouteTargetsInternalPath() {
        // Note: this only confirms there's no *dedicated* /internal route (as instructed).
        // It does NOT prove /api/ai/internal/** is unreachable: Path=/api/ai/** structurally
        // matches it too, since "**" spans sub-paths. Isolation for that path must come from
        // ai-service's own internal-API-key check, not from gateway routing.
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        boolean hasDedicatedInternalRoute = routes.stream()
                .anyMatch(r -> r.getId().toLowerCase().contains("internal"));
        assertThat(hasDedicatedInternalRoute)
                .as("there must be no dedicated route targeting /internal/**")
                .isFalse();
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
