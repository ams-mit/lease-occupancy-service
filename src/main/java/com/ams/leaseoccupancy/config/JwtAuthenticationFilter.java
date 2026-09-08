package com.ams.leaseoccupancy.config;

import com.ams.leaseoccupancy.exception.BusinessException;
import com.ams.leaseoccupancy.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Verifies every request's Gateway-signed JWT (AGENTS.md §8 / the org JWT standard) before
 * it reaches a controller. Runs after {@link RequestIdFilter} so a rejected request still
 * carries a trace ID in its error response.
 *
 * <p>Exceptions are handed to Spring's {@link HandlerExceptionResolver} rather than thrown,
 * because a filter runs outside DispatcherServlet's exception-handling — throwing here would
 * bypass {@code GlobalExceptionHandler} entirely and leak a container-default error page.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> UNAUTHENTICATED_PATH_PREFIXES =
            List.of("/actuator", "/v3/api-docs", "/swagger-ui");

    private final JwtService jwtService;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return UNAUTHENTICATED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractBearerToken(request);
            Claims claims = jwtService.verifyGatewayToken(token);
            AuthContext.set(toPrincipal(claims));
            filterChain.doFilter(request, response);
        } catch (BusinessException ex) {
            exceptionResolver.resolveException(request, response, null, ex);
        } finally {
            AuthContext.clear();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing or malformed Authorization header");
        }
        return header.substring("Bearer ".length());
    }

    @SuppressWarnings("unchecked")
    private AuthContext.Principal toPrincipal(Claims claims) {
        String type = claims.get("type", String.class);
        if (!"user".equals(type) && !"service".equals(type)) {
            throw new InvalidTokenException("Unrecognized token type: " + type);
        }

        List<String> roles = "user".equals(type) ? claims.get("roles", List.class) : List.of();
        return new AuthContext.Principal(type, claims.getSubject(), roles == null ? List.of() : roles);
    }
}
