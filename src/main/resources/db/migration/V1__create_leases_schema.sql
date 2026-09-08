-- Leases: the contractual layer over a unit. unit_id and tenant_id are synthetic
-- foreign keys into property-unit-service and identity-access-service respectively —
-- this service never joins across those schemas directly.
CREATE TABLE leases (
    id UUID PRIMARY KEY,
    unit_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_leases_status CHECK (status IN ('PENDING', 'ACTIVE', 'TERMINATED', 'COMPLETED')),
    CONSTRAINT chk_leases_dates CHECK (end_date > start_date)
);

CREATE INDEX idx_leases_unit_id ON leases (unit_id);
CREATE INDEX idx_leases_tenant_id ON leases (tenant_id);
CREATE INDEX idx_leases_status ON leases (status);

-- Speeds up the date-overlap check run on every lease create/activate: it always
-- filters on unit_id + status = 'ACTIVE' before comparing date ranges.
CREATE INDEX idx_leases_active_unit_dates ON leases (unit_id, start_date, end_date)
    WHERE status = 'ACTIVE';
