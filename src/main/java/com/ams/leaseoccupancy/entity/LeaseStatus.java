package com.ams.leaseoccupancy.entity;

/**
 * Lifecycle states for a {@link Lease}.
 * DRAFT -> PENDING_ACTIVATION -> ACTIVE -> TERMINATED / EXPIRED.
 */
public enum LeaseStatus {
    DRAFT,
    PENDING_ACTIVATION,
    ACTIVE,
    TERMINATED,
    EXPIRED
}
