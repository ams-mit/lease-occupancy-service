package com.ams.leaseoccupancy.service;

import com.ams.leaseoccupancy.client.IdentityServiceClient;
import com.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import com.ams.leaseoccupancy.client.UnitDetailsResponse;
import com.ams.leaseoccupancy.dto.OccupancyCreateRequest;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.entity.Occupancy;
import com.ams.leaseoccupancy.entity.OccupancyStatus;
import com.ams.leaseoccupancy.entity.Occupant;
import com.ams.leaseoccupancy.exception.InvalidTenantException;
import com.ams.leaseoccupancy.exception.LeaseNotFoundException;
import com.ams.leaseoccupancy.exception.OccupancyRuleException;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import com.ams.leaseoccupancy.repository.OccupancyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OccupancyService {
    private final OccupancyRepository occupancies;
    private final LeaseRepository leases;
    private final IdentityServiceClient identity;
    private final PropertyUnitServiceClient property;
    private final UnitLockService locks;

    public OccupancyService(OccupancyRepository occupancies, LeaseRepository leases,
                            IdentityServiceClient identity, PropertyUnitServiceClient property,
                            UnitLockService locks) {
        this.occupancies = occupancies;
        this.leases = leases;
        this.identity = identity;
        this.property = property;
        this.locks = locks;
    }

    @Transactional
    public Occupancy register(OccupancyCreateRequest request) {
        locks.acquireUnitLock(request.unitId());
        Lease lease = leases.findById(request.leaseId())
                .orElseThrow(() -> new LeaseNotFoundException(request.leaseId()));
        if (!lease.getUnitId().equals(request.unitId()) || lease.getStatus() != LeaseStatus.ACTIVE
                || request.moveInDate().isBefore(lease.getStartDate())
                || request.moveInDate().isAfter(lease.getEndDate())) {
            throw new OccupancyRuleException("An active lease covering the move-in date is required");
        }
        boolean permitted = lease.getTenantId().equals(request.residentId())
                || lease.getOccupants().stream().map(Occupant::getResidentId)
                        .anyMatch(request.residentId()::equals);
        if (!permitted) {
            throw new OccupancyRuleException("Resident is not a permitted occupant of this lease");
        }
        if (!identity.validateUser(request.residentId()).isValidTenant()) {
            throw new InvalidTenantException(request.residentId());
        }
        UnitDetailsResponse unit = property.getUnitDetails(request.unitId());
        if (unit.isUnderMaintenance()) {
            throw new OccupancyRuleException("Unit is under maintenance");
        }
        if (occupancies.existsByUnitIdAndResidentIdAndStatus(
                request.unitId(), request.residentId(), OccupancyStatus.ACTIVE)) {
            throw new OccupancyRuleException("Resident already has an active occupancy in this unit");
        }
        if (unit.capacityLimit() > 0
                && occupancies.countByUnitIdAndStatus(request.unitId(), OccupancyStatus.ACTIVE) >= unit.capacityLimit()) {
            throw new OccupancyRuleException("Unit occupancy capacity has been reached");
        }
        Occupancy occupancy = new Occupancy();
        occupancy.setUnitId(request.unitId());
        occupancy.setResidentId(request.residentId());
        occupancy.setLeaseId(request.leaseId());
        occupancy.setMoveInDate(request.moveInDate());
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        return occupancies.save(occupancy);
    }

    @Transactional(readOnly = true)
    public List<Occupancy> currentForUnit(UUID unitId) {
        property.getUnitDetails(unitId);
        return occupancies.findByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Occupancy> historyForResident(UUID residentId) {
        return occupancies.findByResidentIdOrderByMoveInDateDesc(residentId);
    }

    @Transactional
    public Occupancy deactivate(UUID occupancyId) {
        Occupancy occupancy = occupancies.findById(occupancyId)
                .orElseThrow(() -> new OccupancyRuleException("Occupancy record does not exist"));
        locks.acquireUnitLock(occupancy.getUnitId());
        if (occupancy.getStatus() != OccupancyStatus.ACTIVE) {
            throw new OccupancyRuleException("Only an active occupancy can be deactivated");
        }
        occupancy.setStatus(OccupancyStatus.INACTIVE);
        occupancy.setMoveOutDate(LocalDate.now());
        return occupancies.save(occupancy);
    }
}
