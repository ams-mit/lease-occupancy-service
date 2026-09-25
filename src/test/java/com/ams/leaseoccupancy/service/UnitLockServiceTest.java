package com.ams.leaseoccupancy.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ams.leaseoccupancy.entity.UnitLock;
import com.ams.leaseoccupancy.repository.UnitLockRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnitLockServiceTest {

    @Mock
    private UnitLockRepository unitLockRepository;

    @InjectMocks
    private UnitLockService unitLockService;

    @Test
    void acquireUnitLock_createsNewLock_whenNoneExists() {
        UUID unitId = UUID.randomUUID();
        when(unitLockRepository.findByUnitIdForUpdate(unitId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new UnitLock(unitId, Instant.now())));

        unitLockService.acquireUnitLock(unitId);

        verify(unitLockRepository).saveAndFlush(any(UnitLock.class));
    }

    @Test
    void acquireUnitLock_updatesExistingLock_whenExists() {
        UUID unitId = UUID.randomUUID();
        UnitLock existing = new UnitLock(unitId, Instant.now().minusSeconds(10));
        when(unitLockRepository.findByUnitIdForUpdate(unitId)).thenReturn(Optional.of(existing));

        unitLockService.acquireUnitLock(unitId);

        verify(unitLockRepository).save(existing);
    }
}
