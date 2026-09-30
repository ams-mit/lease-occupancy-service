package kln.ams.leaseoccupancy.config;

import java.security.PrivateKey;
import java.security.PublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the two RSA keys this service needs per the JWT standard (AGENTS.md §8):
 * the Gateway's public key, to verify every incoming request, and this service's own
 * private key, to sign outbound Service JWTs. Both are required configuration.
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public PublicKey gatewayPublicKey(@Value("${app.jwt.gateway-public-key:}") String pem) {
        if (pem == null || pem.isBlank()) throw new IllegalStateException("GATEWAY_JWT_PUBLIC_KEY is required");
        return PemUtils.parsePublicKey(pem);
    }

    @Bean
    public PrivateKey servicePrivateKey(@Value("${app.jwt.service-private-key:}") String pem) {
        if (pem == null || pem.isBlank()) throw new IllegalStateException("SERVICE_JWT_PRIVATE_KEY is required");
        return PemUtils.parsePrivateKey(pem);
    }
}
