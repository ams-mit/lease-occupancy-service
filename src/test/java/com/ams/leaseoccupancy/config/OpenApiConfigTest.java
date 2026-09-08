package com.ams.leaseoccupancy.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms SpringDoc actually serves the OpenAPI docs and Swagger UI, and that the
 * public/internal endpoint groups are wired up as separate documentation groups.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiConfigTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void swaggerUiIsServed() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void defaultApiDocsAreServed() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Lease & Occupancy Service API");
    }

    @Test
    void publicAndInternalGroupsAreExposedSeparately() {
        ResponseEntity<String> publicDocs = restTemplate.getForEntity("/v3/api-docs/public", String.class);
        ResponseEntity<String> internalDocs = restTemplate.getForEntity("/v3/api-docs/internal", String.class);

        assertThat(publicDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(internalDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publicGroupDocumentsLeaseEndpoints() {
        ResponseEntity<String> publicDocs = restTemplate.getForEntity("/v3/api-docs/public", String.class);

        assertThat(publicDocs.getBody()).contains("/api/v1/leases");
        assertThat(publicDocs.getBody()).contains("bearerAuth");
    }
}
