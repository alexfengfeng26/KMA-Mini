ALTER TABLE knowledge_doc
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN supersedes_doc_id BIGINT REFERENCES knowledge_doc(doc_id) ON DELETE SET NULL,
    ADD COLUMN activated_at TIMESTAMP;

UPDATE knowledge_doc
SET activated_at = COALESCE(update_time, create_time, now())
WHERE is_active = TRUE;

DROP INDEX IF EXISTS uk_doc_tenant_external_ref;
CREATE UNIQUE INDEX uk_doc_tenant_active_external_ref
    ON knowledge_doc(tenant_id, space_id, external_ref)
    WHERE external_ref IS NOT NULL AND is_active = TRUE;
CREATE UNIQUE INDEX uk_doc_tenant_external_version
    ON knowledge_doc(tenant_id, space_id, external_ref, source_version)
    WHERE external_ref IS NOT NULL;
CREATE INDEX idx_doc_tenant_active_space
    ON knowledge_doc(tenant_id, space_id, is_active);

CREATE TABLE knowledge_ingestion_job (
    job_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    doc_id BIGINT NOT NULL REFERENCES knowledge_doc(doc_id) ON DELETE CASCADE,
    job_type VARCHAR(16) NOT NULL DEFAULT 'ingest',
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 3,
    next_execute_time TIMESTAMP NOT NULL DEFAULT now(),
    lease_owner VARCHAR(128),
    lease_until TIMESTAMP,
    error_message VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_ingestion_job_active_doc
    ON knowledge_ingestion_job(tenant_id, doc_id)
    WHERE status IN ('pending', 'processing');
CREATE INDEX idx_ingestion_job_claim
    ON knowledge_ingestion_job(tenant_id, status, next_execute_time, lease_until);

COMMENT ON TABLE knowledge_ingestion_job IS
    'Durable document ingestion jobs with lease-based multi-node recovery';
