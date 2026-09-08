package com.ams.leaseoccupancy.config;

import com.ams.leaseoccupancy.exception.ForbiddenException;
import com.ams.leaseoccupancy.exception.InvalidTokenException;
import java.util.Arrays;
import java.util.List;

/**
 * Holds the verified caller identity for the current request (set by
 * {@link JwtAuthenticationFilter}) and the role/service authorization checks controllers
 * call explicitly at the top of each method — this service doesn't use Spring Security,
 * so authorization is deliberate and visible in each controller rather than annotation magic.
 */
public final class AuthContext {

    private static final ThreadLocal<Principal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    static void set(Principal principal) {
        CURRENT.set(principal);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static Principal current() {
        Principal principal = CURRENT.get();
        if (principal == null) {
            throw new InvalidTokenException("No authenticated principal on this request");
        }
        return principal;
    }

    /** Requires a {@code type=user} token whose roles include the given role. */
    public static void requireRole(String role) {
        Principal principal = current();
        if (!principal.isUser() || !principal.roles().contains(role)) {
            throw new ForbiddenException("This operation requires the " + role + " role");
        }
    }

    /** Requires a {@code type=service} token whose {@code sub} is one of the allowed callers. */
    public static void requireServiceCaller(String... allowedServices) {
        Principal principal = current();
        boolean allowed = principal.isService() && Arrays.asList(allowedServices).contains(principal.sub());
        if (!allowed) {
            throw new ForbiddenException("Service '" + principal.sub() + "' is not allowed to call this endpoint");
        }
    }

    public record Principal(String type, String sub, List<String> roles) {

        public boolean isUser() {
            return "user".equals(type);
        }

        public boolean isService() {
            return "service".equals(type);
        }
    }
}
