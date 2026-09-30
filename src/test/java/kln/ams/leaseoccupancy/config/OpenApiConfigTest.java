package kln.ams.leaseoccupancy.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms SpringDoc actually serves the OpenAPI docs and Swagger UI, and that the
 * public/internal endpoint groups are wired up as separate documentation groups.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiConfigTest {

    @Value("${local.server.port}")
    private int port;

    private ResponseEntity<String> get(String path) {
        return RestClient.create("http://localhost:" + port).get().uri(path).retrieve().toEntity(String.class);
    }

    @Test
    void swaggerUiIsServed() {
        ResponseEntity<String> response = get("/swagger-ui/index.html");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void defaultApiDocsAreServed() {
        ResponseEntity<String> response = get("/v3/api-docs");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Lease & Occupancy Service API");
    }

    @Test
    void publicAndInternalGroupsAreExposedSeparately() {
        ResponseEntity<String> publicDocs = get("/v3/api-docs/public");
        ResponseEntity<String> internalDocs = get("/v3/api-docs/internal");

        assertThat(publicDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(internalDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publicGroupDocumentsLeaseEndpoints() {
        ResponseEntity<String> publicDocs = get("/v3/api-docs/public");

        assertThat(publicDocs.getBody()).contains("/api/v1/leases");
        assertThat(publicDocs.getBody()).contains("bearerAuth");
        assertThat(publicDocs.getBody()).contains("LEASE-001", "LEASE-002", "LEASE-003", "LEASE-004",
                "LEASE-005", "LEASE-006", "LEASE-008", "LEASE-009", "LEASE-010", "LEASE-011", "LEASE-012");
    }

    @Test
    void internalGroupDocumentsOccupancyEndpoints_requiringBearerAuth() {
        ResponseEntity<String> internalDocs = get("/v3/api-docs/internal");

        assertThat(internalDocs.getBody()).contains("/api/v1/internal/units/{unitId}/occupancy");
        assertThat(internalDocs.getBody()).contains("/api/v1/internal/units/{unitId}/occupants");
        assertThat(internalDocs.getBody()).contains("/api/v1/internal/users/{userId}/occupancy");
        assertThat(internalDocs.getBody()).contains("LEASE-INT-001", "LEASE-INT-002", "LEASE-INT-003",
                "DEPENDENCY_UNAVAILABLE", "X-Request-ID");
        // Per the JWT standard, internal calls carry a Service JWT too —
        // the security requirement is declared globally in OpenApiConfig, not just on "public".
        assertThat(internalDocs.getBody()).contains("\"security\":[{\"bearerAuth\"");
    }
}
