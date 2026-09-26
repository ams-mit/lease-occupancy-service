-- V1__init_lease_occupancy_schema.sql
-- Initializes schemas for leases, lease_occupants, occupancies, and unit_locks.
-- unit_id, tenant_id, and resident_id are synthetic foreign keys
-- owned by property-unit-service and identity-access-service respectively.

-- 1. Leases table: contractual agreement over a unit
CREATE TABLE leases (
    id BINARY(16) PRIMARY KEY,
    unit_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    custom_notes VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_leases_status CHECK (status IN ('DRAFT', 'PENDING_ACTIVATION', 'ACTIVE', 'TERMINATED', 'EXPIRED')),
    CONSTRAINT chk_leases_dates CHECK (end_date > start_date)
);

CREATE INDEX idx_leases_unit_id ON leases (unit_id);
CREATE INDEX idx_leases_tenant_id ON leases (tenant_id);
CREATE INDEX idx_leases_status ON leases (status);

-- Speeds up the date-overlap check on lease creation / activation
CREATE INDEX idx_leases_active_unit_dates ON leases (unit_id, status, start_date, end_date);

-- 2. Lease Occupants table: permitted occupants/residents specified under a contractual lease
CREATE TABLE lease_occupants (
    id BINARY(16) PRIMARY KEY,
    lease_id BINARY(16) NOT NULL,
    resident_id BINARY(16) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lease_occupants_lease_resident UNIQUE (lease_id, resident_id),
    CONSTRAINT fk_lease_occupants_lease FOREIGN KEY (lease_id) REFERENCES leases(id) ON DELETE CASCADE
);

CREATE INDEX idx_lease_occupants_lease_id ON lease_occupants (lease_id);
CREATE INDEX idx_lease_occupants_resident_id ON lease_occupants (resident_id);

-- 3. Occupancies table: physical occupancy records (tracks physical arrival and move-out)
CREATE TABLE occupancies (
    id BINARY(16) PRIMARY KEY,
    unit_id BINARY(16) NOT NULL,
    resident_id BINARY(16) NOT NULL,
    lease_id BINARY(16),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    move_in_date DATE NOT NULL,
    move_out_date DATE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_occupancies_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'RELOCATED')),
    CONSTRAINT chk_occupancies_dates CHECK (move_out_date IS NULL OR move_out_date >= move_in_date),
    CONSTRAINT fk_occupancies_lease FOREIGN KEY (lease_id) REFERENCES leases(id) ON DELETE SET NULL
);

CREATE INDEX idx_occupancies_unit_id ON occupancies (unit_id);
CREATE INDEX idx_occupancies_resident_id ON occupancies (resident_id);
CREATE INDEX idx_occupancies_lease_id ON occupancies (lease_id);
CREATE INDEX idx_occupancies_status ON occupancies (status);

-- 4. Unit Locks table: supports pessimistic concurrency control per unit during lease activations
CREATE TABLE unit_locks (
    unit_id BINARY(16) PRIMARY KEY,
    locked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
