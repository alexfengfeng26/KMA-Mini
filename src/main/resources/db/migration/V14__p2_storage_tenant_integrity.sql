ALTER TABLE knowledge_storage_object
    ADD CONSTRAINT uk_storage_object_tenant_id UNIQUE (tenant_id, object_id);

ALTER TABLE knowledge_doc
    ADD CONSTRAINT uk_knowledge_doc_tenant_id UNIQUE (tenant_id, doc_id);

ALTER TABLE knowledge_doc
    DROP CONSTRAINT knowledge_doc_storage_object_id_fkey,
    ADD CONSTRAINT fk_doc_storage_object_tenant
        FOREIGN KEY (tenant_id, storage_object_id)
        REFERENCES knowledge_storage_object(tenant_id, object_id);

ALTER TABLE knowledge_storage_reference
    DROP CONSTRAINT knowledge_storage_reference_object_id_fkey,
    DROP CONSTRAINT knowledge_storage_reference_doc_id_fkey,
    ADD CONSTRAINT fk_storage_reference_object_tenant
        FOREIGN KEY (tenant_id, object_id)
        REFERENCES knowledge_storage_object(tenant_id, object_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_storage_reference_doc_tenant
        FOREIGN KEY (tenant_id, doc_id)
        REFERENCES knowledge_doc(tenant_id, doc_id) ON DELETE CASCADE;
