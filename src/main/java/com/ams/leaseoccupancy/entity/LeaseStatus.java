package com.ams.leaseoccupancy.entity;

/**
 * Lifecycle states for a {@link Lease}. A lease is created as PENDING and must pass
 * activation prerequisites (unit exists, tenant valid, no date conflict) before
 * becoming ACTIVE.
 */
public enum LeaseStatus {
    PENDING,
    ACTIVE,
    TERMINATED,
    COMPLETED
}
