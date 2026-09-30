ALTER TABLE leases DROP CHECK chk_leases_status;
ALTER TABLE leases DROP CHECK chk_leases_dates;
UPDATE leases SET status = 'PENDING' WHERE status = 'PENDING_ACTIVATION';
ALTER TABLE leases ADD CONSTRAINT chk_leases_status CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'EXPIRED', 'TERMINATED', 'CANCELLED'));
ALTER TABLE leases ADD CONSTRAINT chk_leases_dates CHECK (end_date >= start_date);

ALTER TABLE occupancies DROP CHECK chk_occupancies_status;
UPDATE occupancies SET status = 'ENDED' WHERE status IN ('INACTIVE', 'RELOCATED');
ALTER TABLE occupancies ADD COLUMN notes VARCHAR(255) NULL;
ALTER TABLE occupancies ADD CONSTRAINT chk_occupancies_status CHECK (status IN ('PENDING', 'ACTIVE', 'ENDED', 'CANCELLED'));

CREATE TABLE occupancy_status_history (
    id BINARY(16) PRIMARY KEY,
    occupancy_id BINARY(16) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    reason VARCHAR(255),
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_occupancy_status_history_occupancy FOREIGN KEY (occupancy_id) REFERENCES occupancies(id)
);
CREATE INDEX idx_occupancy_status_history_at ON occupancy_status_history (occupancy_id, changed_at);
