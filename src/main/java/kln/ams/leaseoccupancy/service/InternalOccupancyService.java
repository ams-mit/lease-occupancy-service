package kln.ams.leaseoccupancy.service;

import kln.ams.leaseoccupancy.dto.UnitOccupancyResponse;
import kln.ams.leaseoccupancy.dto.UnitOccupantsResponse;
import kln.ams.leaseoccupancy.dto.UserOccupancyResponse;
import kln.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import kln.ams.leaseoccupancy.entity.Occupancy;
import kln.ams.leaseoccupancy.entity.OccupancyStatus;
import kln.ams.leaseoccupancy.entity.Lease;
import kln.ams.leaseoccupancy.entity.LeaseStatus;
import kln.ams.leaseoccupancy.repository.OccupancyRepository;
import kln.ams.leaseoccupancy.repository.LeaseRepository;
import kln.ams.leaseoccupancy.exception.DependencyUnavailableException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs Gateway-routed internal endpoints protected by Service JWTs (LEASE-INT-001, LEASE-INT-002, LEASE-INT-003).
 */
@Service
public class InternalOccupancyService {

    private final OccupancyRepository occupancyRepository;
    private final LeaseRepository leaseRepository;
    private final PropertyUnitServiceClient propertyUnitServiceClient;

    public InternalOccupancyService(OccupancyRepository occupancyRepository, LeaseRepository leaseRepository,
            PropertyUnitServiceClient propertyUnitServiceClient) {
        this.occupancyRepository = occupancyRepository;
        this.leaseRepository = leaseRepository;
        this.propertyUnitServiceClient = propertyUnitServiceClient;
    }

    /**
     * LEASE-INT-001: Return authoritative occupancy state for a unit.
     */
    @Transactional(readOnly = true)
    public UnitOccupancyResponse getUnitOccupancy(UUID unitId) {
        validateUnit(unitId);
        List<Occupancy> active = currentOccupancies(unitId);
        if (active.isEmpty()) {
            return new UnitOccupancyResponse(unitId, false, null, null, null, null, null);
        }
        Occupancy occ = active.get(0);
        Lease lease = leaseRepository.findById(occ.getLeaseId()).orElseThrow();
        return new UnitOccupancyResponse(
                unitId,
                true,
                occ.getLeaseId(),
                occ.getId(),
                occ.getMoveInDate(),
                lease.getEndDate(),
                occ.getStatus());
    }

    /**
     * LEASE-INT-002: Return current occupants for a unit.
     */
    @Transactional(readOnly = true)
    public UnitOccupantsResponse getUnitOccupants(UUID unitId) {
        validateUnit(unitId);
        List<Occupancy> active = currentOccupancies(unitId);
        List<UnitOccupantsResponse.OccupantItem> items = active.stream()
                .map(occ -> new UnitOccupantsResponse.OccupantItem(
                        occ.getResidentId(),
                        occ.getStatus(),
                        occ.getMoveInDate(),
                        leaseRepository.findById(occ.getLeaseId()).orElseThrow().getEndDate()))
                .toList();
        return new UnitOccupantsResponse(unitId, items);
    }

    /**
     * LEASE-INT-003: Return occupancy relationship for a user.
     */
    @Transactional(readOnly = true)
    public UserOccupancyResponse getUserOccupancy(UUID userId) {
        // A User UUID is not a Resident profile UUID. RES-INT-002 must define the
        // mapping before this endpoint can return authoritative occupancy records.
        throw new DependencyUnavailableException("resident-management-service", null);
    }

    private List<Occupancy> currentOccupancies(UUID unitId) {
        LocalDate today = LocalDate.now();
        return occupancyRepository.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE).stream()
                .filter(occ -> occ.getLeaseId() != null)
                .filter(occ -> occ.getMoveInDate() != null && !occ.getMoveInDate().isAfter(today)
                        && (occ.getMoveOutDate() == null || !occ.getMoveOutDate().isBefore(today)))
                .filter(occ -> leaseRepository.findById(occ.getLeaseId())
                        .filter(lease -> lease.getStatus() == LeaseStatus.ACTIVE
                                && !lease.getStartDate().isAfter(today) && !lease.getEndDate().isBefore(today))
                        .isPresent())
                .toList();
    }

    private void validateUnit(UUID unitId) {
        if (propertyUnitServiceClient.getUnitCapacity(unitId) == null) {
            throw new DependencyUnavailableException("property-unit-service", null);
        }
    }
}
