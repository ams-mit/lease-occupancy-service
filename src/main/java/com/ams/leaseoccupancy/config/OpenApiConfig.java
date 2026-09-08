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
 * SpringDoc OpenAPI setup. Endpoints are split into two documentation groups because
 * they have different audiences and security requirements per the API contract:
 * public/gateway-routed lease and occupancy APIs (JWT-secured) vs. internal
 * service-to-service APIs (private network only, no JWT).
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
                                .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Public API (Gateway-routed, JWT required)")
                .pathsToMatch("/api/v1/leases/**", "/api/v1/occupancies/**")
                .addOpenApiCustomizer(openApi -> openApi.addSecurityItem(
                        new SecurityRequirement().addList(BEARER_SECURITY_SCHEME)))
                .build();
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("Internal API (private network only, no JWT)")
                .pathsToMatch("/api/v1/internal/**")
                .build();
    }
}
