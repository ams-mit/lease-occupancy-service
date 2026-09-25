package com.ams.leaseoccupancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ams.leaseoccupancy.client.IdentityServiceClient;
import com.ams.leaseoccupancy.client.IdentityUserValidation;
import com.ams.leaseoccupancy.client.PropertyUnitServiceClient;
import com.ams.leaseoccupancy.client.UnitCapacityResponse;
import com.ams.leaseoccupancy.dto.LeaseCreateRequest;
import com.ams.leaseoccupancy.dto.LeaseStatusUpdateRequest;
import com.ams.leaseoccupancy.entity.Lease;
import com.ams.leaseoccupancy.entity.LeaseStatus;
import com.ams.leaseoccupancy.exception.CapacityLimitExceededException;
import com.ams.leaseoccupancy.exception.InvalidLeaseDatesException;
import com.ams.leaseoccupancy.exception.InvalidLeaseStatusTransitionException;
import com.ams.leaseoccupancy.exception.InvalidTenantException;
import com.ams.leaseoccupancy.exception.LeaseConflictException;
import com.ams.leaseoccupancy.exception.LeaseNotFoundException;
import com.ams.leaseoccupancy.repository.LeaseRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaseServiceTest {

    @Mock
    private LeaseRepository leaseRepository;

    @Mock
    private IdentityServiceClient identityServiceClient;

    @Mock
    private PropertyUnitServiceClient propertyUnitServiceClient;

    @Mock
    private UnitLockService unitLockService;

    @InjectMocks
    private LeaseService leaseService;

    private UUID unitId;
    private UUID tenantId;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        unitId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        startDate = LocalDate.now().plusDays(1);
        endDate = LocalDate.now().plusMonths(6);
    }

    @Test
    void createLease_savesAsDraft_whenTenantValidAndNoConflict() {
        LeaseCreateRequest request = new LeaseCreateRequest(unitId, tenantId, startDate, endDate);
        when(identityServiceClient.validateUser(tenantId))
                .thenReturn(new IdentityUserValidation(tenantId, true, true));
        when(propertyUnitServiceClient.getUnitCapacity(unitId))
                .thenReturn(new UnitCapacityResponse(unitId, 1));
        when(leaseRepository.countOverlappingActiveLeases(eq(unitId), eq(startDate), eq(endDate), isNull()))
                .thenReturn(0L);
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease result = leaseService.createLease(request);

        assertThat(result.getStatus()).isEqualTo(LeaseStatus.DRAFT);
        assertThat(result.getUnitId()).isEqualTo(unitId);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void createLease_rejects_whenEndDateNotAfterStartDate() {
        LeaseCreateRequest request = new LeaseCreateRequest(unitId, tenantId, startDate, startDate);

        assertThatThrownBy(() -> leaseService.createLease(request))
                .isInstanceOf(InvalidLeaseDatesException.class);
    }

    @Test
    void createLease_rejects_whenTenantNotValid() {
        LeaseCreateRequest request = new LeaseCreateRequest(unitId, tenantId, startDate, endDate);
        when(identityServiceClient.validateUser(tenantId))
                .thenReturn(new IdentityUserValidation(tenantId, true, false));

        assertThatThrownBy(() -> leaseService.createLease(request))
                .isInstanceOf(InvalidTenantException.class);
    }

    @Test
    void createLease_rejects_whenUnitHasOverlappingActiveLease_capacity1() {
        LeaseCreateRequest request = new LeaseCreateRequest(unitId, tenantId, startDate, endDate);
        when(identityServiceClient.validateUser(tenantId))
                .thenReturn(new IdentityUserValidation(tenantId, true, true));
        when(propertyUnitServiceClient.getUnitCapacity(unitId))
                .thenReturn(new UnitCapacityResponse(unitId, 1));
        when(leaseRepository.countOverlappingActiveLeases(eq(unitId), eq(startDate), eq(endDate), isNull()))
                .thenReturn(1L);

        assertThatThrownBy(() -> leaseService.createLease(request))
                .isInstanceOf(LeaseConflictException.class);
    }

    @Test
    void updateStatus_throwsNotFound_whenLeaseMissing() {
        UUID leaseId = UUID.randomUUID();
        when(leaseRepository.findById(leaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaseService.updateStatus(leaseId, new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null)))
                .isInstanceOf(LeaseNotFoundException.class);
    }

    @Test
    void updateStatus_rejects_illegalTransitionFromTerminated() {
        Lease lease = existingLeaseWithStatus(LeaseStatus.TERMINATED);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));

        assertThatThrownBy(() -> leaseService.updateStatus(lease.getId(), new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null)))
                .isInstanceOf(InvalidLeaseStatusTransitionException.class);
    }

    @Test
    void updateStatus_activates_whenCapacity1AndNoConflict() {
        Lease lease = existingLeaseWithStatus(LeaseStatus.DRAFT);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(propertyUnitServiceClient.getUnitCapacity(lease.getUnitId()))
                .thenReturn(new UnitCapacityResponse(lease.getUnitId(), 1));
        when(leaseRepository.countOverlappingActiveLeases(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId()))
                .thenReturn(0L);
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease result = leaseService.updateStatus(lease.getId(), new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null));

        assertThat(result.getStatus()).isEqualTo(LeaseStatus.ACTIVE);
        verify(unitLockService).acquireUnitLock(lease.getUnitId());
    }

    @Test
    void updateStatus_rejectsWithConflict_whenCapacity1AndDatesOverlap() {
        // Acceptance Scenario: Rejection of Overlapping Date Ranges (Single Unit, capacity = 1) -> 409
        Lease lease = existingLeaseWithStatus(LeaseStatus.DRAFT);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(propertyUnitServiceClient.getUnitCapacity(lease.getUnitId()))
                .thenReturn(new UnitCapacityResponse(lease.getUnitId(), 1));
        when(leaseRepository.countOverlappingActiveLeases(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId()))
                .thenReturn(1L);

        assertThatThrownBy(() -> leaseService.updateStatus(lease.getId(), new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null)))
                .isInstanceOf(LeaseConflictException.class);
        verify(unitLockService).acquireUnitLock(lease.getUnitId());
    }

    @Test
    void updateStatus_activates_whenMultiOccupancyUnderCapacity() {
        // Acceptance Scenario: Multi-Occupancy Under Capacity (capacity = 3, 2 active tenants -> 3rd activates successfully)
        Lease lease = existingLeaseWithStatus(LeaseStatus.DRAFT);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(propertyUnitServiceClient.getUnitCapacity(lease.getUnitId()))
                .thenReturn(new UnitCapacityResponse(lease.getUnitId(), 3));
        when(leaseRepository.countOverlappingActiveLeases(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId()))
                .thenReturn(2L);
        when(leaseRepository.save(any(Lease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lease result = leaseService.updateStatus(lease.getId(), new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null));

        assertThat(result.getStatus()).isEqualTo(LeaseStatus.ACTIVE);
        verify(unitLockService).acquireUnitLock(lease.getUnitId());
    }

    @Test
    void updateStatus_rejectsWith422_whenMultiOccupancyOverCapacity() {
        // Acceptance Scenario: Multi-Occupancy Over Capacity (capacity = 3, 3 active leases -> 4th rejected with 422)
        Lease lease = existingLeaseWithStatus(LeaseStatus.DRAFT);
        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(propertyUnitServiceClient.getUnitCapacity(lease.getUnitId()))
                .thenReturn(new UnitCapacityResponse(lease.getUnitId(), 3));
        when(leaseRepository.countOverlappingActiveLeases(lease.getUnitId(), lease.getStartDate(), lease.getEndDate(), lease.getId()))
                .thenReturn(3L);

        assertThatThrownBy(() -> leaseService.updateStatus(lease.getId(), new LeaseStatusUpdateRequest(LeaseStatus.ACTIVE, null)))
                .isInstanceOf(CapacityLimitExceededException.class);
        verify(unitLockService).acquireUnitLock(lease.getUnitId());
    }

    @Test
    void isTenantActiveInUnit_delegatesToRepository() {
        when(leaseRepository.existsByUnitIdAndTenantIdAndStatus(unitId, tenantId, LeaseStatus.ACTIVE))
                .thenReturn(true);

        boolean active = leaseService.isTenantActiveInUnit(tenantId, unitId);
        assertThat(active).isTrue();
    }

    private Lease existingLeaseWithStatus(LeaseStatus status) {
        Lease lease = new Lease();
        lease.setId(UUID.randomUUID());
        lease.setUnitId(unitId);
        lease.setTenantId(tenantId);
        lease.setStartDate(startDate);
        lease.setEndDate(endDate);
        lease.setStatus(status);
        return lease;
    }
}
