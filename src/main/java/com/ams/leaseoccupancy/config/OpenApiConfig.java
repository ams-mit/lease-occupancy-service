package com.ams.leaseoccupancy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI setup. Endpoints are split into two documentation groups by audience —
 * public/gateway-routed lease and occupancy APIs (User JWT) vs. internal service-to-service
 * APIs (Service JWT) — but per the JWT standard (AGENTS.md §8) both carry a Gateway-issued
 * bearer token, so the security requirement is declared globally rather than per-group.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI leaseOccupancyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lease & Occupancy Service API")
                        .description("Manages lease contracts and physical occupancy records "
                                + "for the Apartment Management System.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SECURITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Gateway-issued JWT — a User JWT (type=user) on public endpoints, "
                                        + "a Service JWT (type=service) on internal ones.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SECURITY_SCHEME));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Public API (Gateway-routed, User JWT required)")
                .pathsToMatch("/api/v1/leases/**", "/api/v1/occupancies/**")
                .build();
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("Internal API (Gateway-routed, Service JWT required)")
                .pathsToMatch("/api/v1/internal/**")
                .build();
    }
}
