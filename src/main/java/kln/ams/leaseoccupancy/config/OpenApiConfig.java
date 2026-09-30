package kln.ams.leaseoccupancy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
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
                .pathsToMatch("/api/v1/leases/**", "/api/v1/occupancies/**", "/api/v1/units/**")
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

    @Bean
    public GlobalOperationCustomizer canonicalOperationDocs() {
        return (operation, handlerMethod) -> {
            String id = operation.getOperationId();
            if (id == null || !id.startsWith("LEASE-")) return operation;
            if (operation.getResponses() == null) operation.setResponses(new ApiResponses());
            operation.addParametersItem(new Parameter().in("header").name("X-Request-ID")
                    .description("UUID propagated across synchronous calls; generated when absent")
                    .required(false).schema(new StringSchema().format("uuid")));
            operation.getResponses().addApiResponse("401", new ApiResponse().description("UNAUTHORIZED: missing or invalid Gateway JWT"));
            operation.getResponses().addApiResponse("403", new ApiResponse().description("FORBIDDEN: role, scope, or service caller denied"));
            operation.getResponses().addApiResponse("503", new ApiResponse().description("DEPENDENCY_UNAVAILABLE: required provider could not give an authoritative answer"));
            operation.getResponses().addApiResponse("500", new ApiResponse().description("INTERNAL_SERVER_ERROR"));
            if (id.equals("LEASE-001") || id.equals("LEASE-006") || id.equals("LEASE-008") || id.equals("LEASE-011")) {
                operation.getResponses().addApiResponse("400", new ApiResponse().description("VALIDATION_ERROR"));
                operation.getResponses().addApiResponse("409", new ApiResponse().description("Lease or occupancy state/date conflict"));
            }
            if (!id.equals("LEASE-002")) {
                operation.getResponses().addApiResponse("404", new ApiResponse().description("Referenced lease, occupancy, unit, or resident not found"));
            }
            return operation;
        };
    }
}
