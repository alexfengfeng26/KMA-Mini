ALTER TABLE knowledge_doc
    ADD COLUMN storage_size_bytes BIGINT NOT NULL DEFAULT 0;

ALTER TABLE knowledge_call_log
    ADD COLUMN security_flags VARCHAR(512) NOT NULL DEFAULT '[]';

CREATE TABLE kma_tenant_quota (
    tenant_id VARCHAR(64) PRIMARY KEY REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    max_documents BIGINT NOT NULL DEFAULT 100000,
    max_storage_bytes BIGINT NOT NULL DEFAULT 107374182400,
    max_concurrent_requests INT NOT NULL DEFAULT 100,
    daily_token_limit BIGINT NOT NULL DEFAULT 100000000,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (max_documents > 0),
    CHECK (max_storage_bytes > 0),
    CHECK (max_concurrent_requests > 0),
    CHECK (daily_token_limit > 0)
);

INSERT INTO kma_tenant_quota(tenant_id)
SELECT tenant_id FROM kma_tenant ON CONFLICT DO NOTHING;

CREATE TABLE kma_tenant_daily_usage (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    token_count BIGINT NOT NULL DEFAULT 0,
    request_count BIGINT NOT NULL DEFAULT 0,
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, usage_date),
    CHECK (token_count >= 0),
    CHECK (request_count >= 0)
);

CREATE TABLE kma_request_lease (
    lease_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    subject_id VARCHAR(128),
    request_path VARCHAR(512),
    expires_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_request_lease_tenant_expiry ON kma_request_lease(tenant_id, expires_at);

CREATE TABLE kma_rate_limit_bucket (
    bucket_key VARCHAR(64) NOT NULL,
    window_start BIGINT NOT NULL,
    used INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (bucket_key, window_start)
);
CREATE INDEX idx_rate_limit_bucket_expiry ON kma_rate_limit_bucket(expires_at);

CREATE TABLE kma_security_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64),
    subject_id VARCHAR(128),
    username VARCHAR(128),
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource VARCHAR(256),
    content_hash VARCHAR(64),
    flags JSONB NOT NULL DEFAULT '[]'::jsonb,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (severity IN ('info','warning','high','critical'))
);
CREATE INDEX idx_security_audit_tenant_time ON kma_security_audit(tenant_id, create_time DESC);
CREATE INDEX idx_security_audit_event_time ON kma_security_audit(event_type, create_time DESC);

INSERT INTO kma_permission(permission_code, name) VALUES
    ('quota:manage', '租户配额管理'),
    ('security:audit', '安全审计查看')
ON CONFLICT DO NOTHING;
