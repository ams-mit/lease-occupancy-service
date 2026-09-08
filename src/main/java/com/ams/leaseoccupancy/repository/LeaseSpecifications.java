package com.ams.leaseoccupancy.repository;

import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters for {@code GET /api/v1/leases} — status and an active-on-date window. */
public final class LeaseSpecifications {

    private LeaseSpecifications() {
    }

    public static Specification<Lease> hasStatus(LeaseStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** Matches leases whose [startDate, endDate] range includes the given date. */
    public static Specification<Lease> activeOn(LocalDate date) {
        return (root, query, cb) -> date == null ? null : cb.and(
                cb.lessThanOrEqualTo(root.get("startDate"), date),
                cb.greaterThanOrEqualTo(root.get("endDate"), date));
    }
}
