CREATE TABLE knowledge_storage_object (
    object_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    location VARCHAR(1024) NOT NULL,
    storage_type VARCHAR(16) NOT NULL,
    checksum_algorithm VARCHAR(16) NOT NULL DEFAULT 'SHA-256',
    checksum VARCHAR(128),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'active',
    last_reconciled_at TIMESTAMP,
    delete_after TIMESTAMP,
    deleted_at TIMESTAMP,
    error_message VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, location),
    CHECK (size_bytes >= 0),
    CHECK (status IN ('active','orphan','missing','corrupt','deleting','delete_failed','deleted'))
);
CREATE INDEX idx_storage_object_tenant_status ON knowledge_storage_object(tenant_id, status, update_time);
CREATE INDEX idx_storage_object_cleanup ON knowledge_storage_object(status, delete_after)
    WHERE status IN ('orphan','delete_failed');

CREATE TABLE knowledge_storage_reference (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    object_id BIGINT NOT NULL REFERENCES knowledge_storage_object(object_id) ON DELETE CASCADE,
    doc_id BIGINT NOT NULL REFERENCES knowledge_doc(doc_id) ON DELETE CASCADE,
    reference_type VARCHAR(32) NOT NULL DEFAULT 'source',
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, object_id, doc_id, reference_type),
    UNIQUE (tenant_id, doc_id, reference_type)
);
CREATE INDEX idx_storage_reference_object ON knowledge_storage_reference(tenant_id, object_id);

ALTER TABLE knowledge_doc ADD COLUMN storage_object_id BIGINT REFERENCES knowledge_storage_object(object_id);
CREATE INDEX idx_doc_storage_object ON knowledge_doc(tenant_id, storage_object_id);

INSERT INTO knowledge_storage_object
    (tenant_id, location, storage_type, checksum_algorithm, checksum, size_bytes, status)
SELECT tenant_id, storage_path,
       CASE WHEN storage_path LIKE 's3://%' THEN 'minio' ELSE 'local' END,
       'MD5', content_hash, storage_size_bytes, 'active'
FROM knowledge_doc
WHERE storage_path IS NOT NULL AND storage_path <> ''
ON CONFLICT (tenant_id, location) DO NOTHING;

UPDATE knowledge_doc d SET storage_object_id=o.object_id
FROM knowledge_storage_object o
WHERE o.tenant_id=d.tenant_id AND o.location=d.storage_path AND d.storage_object_id IS NULL;

INSERT INTO knowledge_storage_reference(tenant_id, object_id, doc_id, reference_type)
SELECT tenant_id, storage_object_id, doc_id, 'source'
FROM knowledge_doc WHERE storage_object_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO kma_permission(permission_code, name) VALUES
    ('storage:manage', '对象存储生命周期管理')
ON CONFLICT DO NOTHING;
