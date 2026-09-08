package com.ams.leaseoccupancy.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * REST clients for calling sibling microservices. Bounded connect/read timeouts per
 * API-STANDARD-v1 §29 — this service must never wait indefinitely on a dependency.
 */
@Configuration
public class ServiceClientsConfig {

    @Bean
    public RestClient identityServiceRestClient(
            @Value("${app.services.identity-base-url}") String baseUrl,
            @Value("${app.services.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${app.services.read-timeout-ms}") long readTimeoutMs) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
