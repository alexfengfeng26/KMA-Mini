CREATE TABLE kma_evaluation_dataset (
    evaluation_dataset_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    name VARCHAR(128) NOT NULL,
    space_code VARCHAR(64) NOT NULL,
    description VARCHAR(512),
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TABLE kma_evaluation_case (
    evaluation_case_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    evaluation_dataset_id BIGINT NOT NULL REFERENCES kma_evaluation_dataset(evaluation_dataset_id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    expected_external_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_answer TEXT,
    should_refuse BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE kma_evaluation_run (
    evaluation_run_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    evaluation_dataset_id BIGINT NOT NULL REFERENCES kma_evaluation_dataset(evaluation_dataset_id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    top_k INT NOT NULL,
    metrics JSONB,
    error_message VARCHAR(1000),
    start_time TIMESTAMP NOT NULL DEFAULT now(),
    end_time TIMESTAMP
);

CREATE TABLE kma_evaluation_result (
    evaluation_result_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    evaluation_run_id BIGINT NOT NULL REFERENCES kma_evaluation_run(evaluation_run_id) ON DELETE CASCADE,
    evaluation_case_id BIGINT NOT NULL REFERENCES kma_evaluation_case(evaluation_case_id) ON DELETE CASCADE,
    retrieved_external_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    recall_at_k NUMERIC(8,6) NOT NULL,
    reciprocal_rank NUMERIC(8,6) NOT NULL,
    citation_precision NUMERIC(8,6) NOT NULL,
    refusal_correct BOOLEAN NOT NULL,
    latency_millis INT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_evaluation_run_dataset ON kma_evaluation_run(tenant_id, evaluation_dataset_id, start_time DESC);
