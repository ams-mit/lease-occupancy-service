package kln.ams.leaseoccupancy.config;

import kln.ams.leaseoccupancy.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies inbound Gateway-signed JWTs and mints outbound Service JWTs, per
 * PROJECT-A-JWT-SECURITY-STANDARD.md. This service never signs or
 * verifies anything but its own outbound Service JWT and the Gateway's tokens — it
 * never needs another backend service's key (§10-11 of the standard).
 */
@Component
public class JwtService {

    private static final Set<String> ROLES = Set.of("SYSTEM_ADMINISTRATOR", "APARTMENT_MANAGER", "OWNER",
            "TENANT_RESIDENT", "FINANCE_OFFICER", "MAINTENANCE_COORDINATOR", "TECHNICIAN",
            "SERVICE_STAFF", "SECURITY_OFFICER");

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
            var jwt = Jwts.parser()
                    .verifyWith(gatewayPublicKey)
                    .build()
                    .parseSignedClaims(token);
            if (!"RS256".equals(jwt.getHeader().getAlgorithm())
                    || !"JWT".equals(jwt.getHeader().getType())
                    || jwt.getHeader().getKeyId() != null) throw new IllegalArgumentException("Unsupported JWT header");
            Claims claims = jwt.getPayload();
            if (claims.containsKey("iss") || claims.containsKey("aud")) throw new IllegalArgumentException("Unsupported JWT claims");
            if (claims.getSubject() == null || claims.getIssuedAt() == null || claims.getExpiration() == null
                    || !claims.getExpiration().after(claims.getIssuedAt()) || claims.getIssuedAt().after(new Date())) {
                throw new IllegalArgumentException("Invalid JWT claims");
            }
            if ("user".equals(claims.get("type"))) {
                UUID.fromString(claims.getSubject());
                Object rawRoles = claims.get("roles");
                if (!(rawRoles instanceof List<?> roles) || roles.isEmpty()
                        || roles.stream().anyMatch(role -> !(role instanceof String value) || !ROLES.contains(value))) {
                    throw new IllegalArgumentException("Invalid user roles");
                }
            } else if ("service".equals(claims.get("type"))) {
                if (claims.containsKey("roles") || !claims.getSubject().matches("[a-z]+(?:-[a-z]+)*-service")) {
                    throw new IllegalArgumentException("Invalid service claims");
                }
            } else throw new IllegalArgumentException("Invalid JWT type");
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired token", ex);
        }
    }

    /** Mints a short-lived {@code type=service} JWT identifying this service, signed with our own key. */
    public String mintServiceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().type("JWT").and()
                .subject(serviceName)
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(serviceJwtTtl)))
                .signWith(servicePrivateKey, Jwts.SIG.RS256)
                .compact();
    }
}
