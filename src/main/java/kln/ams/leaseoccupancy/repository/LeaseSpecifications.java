package kln.ams.leaseoccupancy.repository;

import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.entity.Occupant;
import jakarta.persistence.criteria.Join;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filter specifications for LEASE-002: GET /api/v1/leases.
 */
public final class LeaseSpecifications {

    private LeaseSpecifications() {
    }

    public static Specification<Lease> hasStatus(LeaseStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Lease> hasUnitId(UUID unitId) {
        return (root, query, cb) -> unitId == null ? null : cb.equal(root.get("unitId"), unitId);
    }

    public static Specification<Lease> hasTenantOrOccupantId(UUID residentId) {
        if (residentId == null) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Lease, Occupant> occupantsJoin = root.join("occupants");
            return cb.or(
                    cb.equal(root.get("tenantId"), residentId),
                    cb.equal(occupantsJoin.get("residentId"), residentId)
            );
        };
    }

    public static Specification<Lease> unitIdIn(Collection<UUID> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("unitId").in(unitIds);
    }

    public static Specification<Lease> startDateOnOrAfter(LocalDate startDate) {
        return (root, query, cb) -> startDate == null ? null : cb.greaterThanOrEqualTo(root.get("startDate"), startDate);
    }

    public static Specification<Lease> endDateOnOrBefore(LocalDate endDate) {
        return (root, query, cb) -> endDate == null ? null : cb.lessThanOrEqualTo(root.get("endDate"), endDate);
    }

    /** Matches leases whose [startDate, endDate] range includes the given date. */
    public static Specification<Lease> activeOn(LocalDate date) {
        return (root, query, cb) -> date == null ? null : cb.and(
                cb.lessThanOrEqualTo(root.get("startDate"), date),
                cb.greaterThanOrEqualTo(root.get("endDate"), date));
    }
}
