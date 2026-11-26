package com.MyAnimaLog.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
    void calendarRoute_forwardsPathUnchanged_noRewrite() {
        Route route = findRoute("calendar-service");
        assertThat(hasRewritePathFilter(route))
                .as("calendar-service must NOT strip /api: Calendar serves its routes under /api")
                .isFalse();
    }

    @Test
    void calendarRoute_matchesVeterinariesAndMyAppointments() {
        Route route = findRoute("calendar-service");
        assertThat(matches(route, "/api/veterinaries/x/services")).isTrue();
        assertThat(matches(route, "/api/me/appointments")).isTrue();
        assertThat(matches(route, "/api/calendar-events/1")).isTrue();
    }

    @Test
    void noRouteMatchesCalendarInternalJobsPath() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        boolean anyRouteMatches = routes.stream()
                .anyMatch(r -> matches(r, "/internal/jobs/appointments/reminders"));
        assertThat(anyRouteMatches)
                .as("/internal/jobs/appointments/reminders (Cloud Scheduler-only) must not be reachable")
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

    @Test
    void everyRouteHasDedupeResponseHeaderDefaultFilter() {
        // Default-filters apply to all routes; checked here on a couple of them to
        // confirm it's actually wired, since otherwise a downstream service's own
        // Access-Control-Allow-Origin/-Credentials would leak through duplicated.
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        for (String id : List.of("user-register", "medical-pet-service", "calendar-service")) {
            Route route = routes.stream().filter(r -> r.getId().equals(id)).findFirst()
                    .orElseThrow(() -> new AssertionError("Route not found: " + id));
            assertThat(route.getFilters().stream().anyMatch(f -> f.toString().contains("DedupeResponseHeader")))
                    .as("route %s must carry the DedupeResponseHeader default-filter", id)
                    .isTrue();
        }
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

    private ServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private boolean matches(Route route, String path) {
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchangeFor(path))).block());
    }
}
