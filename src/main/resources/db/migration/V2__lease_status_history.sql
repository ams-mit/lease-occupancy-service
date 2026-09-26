CREATE TABLE lease_status_history (
    id BINARY(16) PRIMARY KEY,
    lease_id BINARY(16) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    reason VARCHAR(255),
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_lease_status_history_lease FOREIGN KEY (lease_id) REFERENCES leases(id)
);

CREATE INDEX idx_lease_status_history_lease_at ON lease_status_history (lease_id, changed_at);
