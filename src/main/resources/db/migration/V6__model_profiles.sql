CREATE TABLE kma_model_profile (
    profile_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    profile_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    capability VARCHAR(16) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    base_url VARCHAR(512),
    dimension INT,
    timeout_seconds INT NOT NULL DEFAULT 60,
    secret_alias VARCHAR(128),
    fallback_profile_codes JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, profile_code),
    CHECK (capability IN ('llm', 'embedding', 'rerank', 'ocr')),
    CHECK (dimension IS NULL OR dimension IN (768, 1024, 1536))
);
CREATE INDEX idx_model_profile_capability
    ON kma_model_profile(tenant_id, capability, enabled);
