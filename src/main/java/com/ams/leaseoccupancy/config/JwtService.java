package com.ams.leaseoccupancy.config;

import com.ams.leaseoccupancy.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies inbound Gateway-signed JWTs and mints outbound Service JWTs, per
 * Project_A_JWT_Authentication_and_Security_Standard.md. This service never signs or
 * verifies anything but its own outbound Service JWT and the Gateway's tokens — it
 * never needs another backend service's key (§10-11 of the standard).
 */
@Component
public class JwtService {

    private final PublicKey gatewayPublicKey;
    private final PrivateKey servicePrivateKey;
    private final String serviceName;
    private final Duration serviceJwtTtl;

    public JwtService(
            PublicKey gatewayPublicKey,
            PrivateKey servicePrivateKey,
            @Value("${app.jwt.service-name}") String serviceName,
            @Value("${app.jwt.service-jwt-ttl-seconds}") long serviceJwtTtlSeconds) {
        this.gatewayPublicKey = gatewayPublicKey;
        this.servicePrivateKey = servicePrivateKey;
        this.serviceName = serviceName;
        this.serviceJwtTtl = Duration.ofSeconds(serviceJwtTtlSeconds);
    }

    /** Verifies a Gateway-signed JWT (RS256, Gateway public key) and returns its claims. */
    public Claims verifyGatewayToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(gatewayPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired token", ex);
        }
    }

    /** Mints a short-lived {@code type=service} JWT identifying this service, signed with our own key. */
    public String mintServiceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(serviceName)
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(serviceJwtTtl)))
                .signWith(servicePrivateKey, Jwts.SIG.RS256)
                .compact();
    }
}
