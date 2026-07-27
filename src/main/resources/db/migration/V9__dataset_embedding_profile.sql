ALTER TABLE knowledge_dataset
    ADD COLUMN embedding_profile_code VARCHAR(64);

ALTER TABLE knowledge_dataset
    ADD CONSTRAINT fk_dataset_embedding_profile
    FOREIGN KEY (tenant_id, embedding_profile_code)
    REFERENCES kma_model_profile(tenant_id, profile_code);

CREATE INDEX idx_dataset_embedding_profile
    ON knowledge_dataset(tenant_id, embedding_profile_code)
    WHERE embedding_profile_code IS NOT NULL;
