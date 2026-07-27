CREATE TABLE kma_idempotency_record (
    record_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    key_hash CHAR(64) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    target_hash CHAR(64) NOT NULL,
    request_target VARCHAR(512) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'processing',
    response_status INT,
    response_content_type VARCHAR(255),
    response_body BYTEA,
    expires_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, key_hash, http_method, target_hash),
    CHECK (state IN ('processing', 'completed'))
);

CREATE INDEX idx_idempotency_expiry ON kma_idempotency_record(expires_at);
