package com.ams.leaseoccupancy.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the two RSA keys this service needs per the JWT standard (AGENTS.md §8):
 * the Gateway's public key, to verify every incoming request, and this service's own
 * private key, to sign outbound Service JWTs. Both are read from PEM env vars; if
 * either is unset, an ephemeral keypair is generated so the service still boots
 * locally — that keypair will never match a real Gateway, so it must not be relied on
 * anywhere the real Gateway is involved.
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    public PublicKey gatewayPublicKey(@Value("${app.jwt.gateway-public-key:}") String pem) {
        if (pem == null || pem.isBlank()) {
            log.warn("GATEWAY_JWT_PUBLIC_KEY is not configured — generating an ephemeral RSA keypair for local "
                    + "development. Every real Gateway-signed token will be rejected until the real key is set.");
            return generateKeyPair().getPublic();
        }
        return PemUtils.parsePublicKey(pem);
    }

    @Bean
    public PrivateKey servicePrivateKey(@Value("${app.jwt.service-private-key:}") String pem) {
        if (pem == null || pem.isBlank()) {
            log.warn("SERVICE_JWT_PRIVATE_KEY is not configured — generating an ephemeral RSA keypair for local "
                    + "development. Register the real public key with the Gateway before deploying anywhere shared.");
            return generateKeyPair().getPrivate();
        }
        return PemUtils.parsePrivateKey(pem);
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA algorithm not available", ex);
        }
    }
}
