ALTER TABLE kma_model_profile
    ADD COLUMN default_profile BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uk_model_profile_default_capability
    ON kma_model_profile(tenant_id, capability)
    WHERE default_profile = TRUE AND enabled = TRUE;

CREATE TABLE kma_embedding_rebuild_job (
    job_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    dataset_id BIGINT NOT NULL REFERENCES knowledge_dataset(dataset_id),
    source_profile_code VARCHAR(64),
    target_profile_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    total_chunks BIGINT NOT NULL DEFAULT 0,
    processed_chunks BIGINT NOT NULL DEFAULT 0,
    cursor_chunk_id BIGINT,
    metrics JSONB,
    error_message VARCHAR(1000),
    lease_owner VARCHAR(128),
    lease_until TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    activated_at TIMESTAMP,
    CHECK (status IN ('pending', 'running', 'validating', 'ready', 'activated', 'failed')),
    FOREIGN KEY (tenant_id, source_profile_code)
        REFERENCES kma_model_profile(tenant_id, profile_code),
    FOREIGN KEY (tenant_id, target_profile_code)
        REFERENCES kma_model_profile(tenant_id, profile_code)
);

CREATE UNIQUE INDEX uk_embedding_rebuild_active_dataset
    ON kma_embedding_rebuild_job(tenant_id, dataset_id)
    WHERE status IN ('pending', 'running', 'validating', 'ready');

CREATE INDEX idx_embedding_rebuild_status
    ON kma_embedding_rebuild_job(tenant_id, status, create_time);

ALTER TABLE kma_evaluation_result
    ADD COLUMN generated_answer TEXT,
    ADD COLUMN answer_correctness NUMERIC(8,6) NOT NULL DEFAULT 0,
    ADD COLUMN judge_reason VARCHAR(1000);

ALTER TABLE kma_evaluation_run
    ADD COLUMN gate_passed BOOLEAN,
    ADD COLUMN gate_failures JSONB;

CREATE TABLE kma_evaluation_gate (
    gate_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    evaluation_dataset_id BIGINT NOT NULL REFERENCES kma_evaluation_dataset(evaluation_dataset_id) ON DELETE CASCADE,
    min_recall_at_k NUMERIC(8,6) NOT NULL DEFAULT 0.80,
    min_mrr NUMERIC(8,6) NOT NULL DEFAULT 0.60,
    min_citation_precision NUMERIC(8,6) NOT NULL DEFAULT 0.80,
    min_refusal_accuracy NUMERIC(8,6) NOT NULL DEFAULT 0.90,
    min_answer_correctness NUMERIC(8,6) NOT NULL DEFAULT 0.70,
    min_case_count INT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, evaluation_dataset_id)
);
