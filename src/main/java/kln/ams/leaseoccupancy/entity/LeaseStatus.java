package kln.ams.leaseoccupancy.entity;

/**
 * Lifecycle states for a {@link Lease}.
 * DRAFT -> PENDING -> ACTIVE -> TERMINATED / EXPIRED.
 */
public enum LeaseStatus {
    DRAFT,
    PENDING,
    ACTIVE,
    TERMINATED,
    EXPIRED,
    CANCELLED
}
