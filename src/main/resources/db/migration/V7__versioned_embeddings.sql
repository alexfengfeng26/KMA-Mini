CREATE TABLE knowledge_chunk_embedding (
    embedding_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    chunk_id BIGINT NOT NULL REFERENCES knowledge_chunk(chunk_id) ON DELETE CASCADE,
    space_id BIGINT NOT NULL,
    profile_code VARCHAR(128) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    dimension INT NOT NULL,
    embedding VECTOR NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, chunk_id, profile_code),
    CHECK (dimension IN (768, 1024, 1536)),
    CHECK (vector_dims(embedding) = dimension)
);

INSERT INTO knowledge_chunk_embedding
    (tenant_id, chunk_id, space_id, profile_code, model_name, dimension, embedding)
SELECT tenant_id, chunk_id, space_id,
       'legacy:' || COALESCE(embedding_model, 'unknown'),
       COALESCE(embedding_model, 'unknown'), 1024, embedding
FROM knowledge_chunk
WHERE embedding IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX idx_chunk_embedding_768_hnsw ON knowledge_chunk_embedding
    USING hnsw ((embedding::vector(768)) vector_cosine_ops)
    WHERE dimension = 768 AND active = TRUE;
CREATE INDEX idx_chunk_embedding_1024_hnsw ON knowledge_chunk_embedding
    USING hnsw ((embedding::vector(1024)) vector_cosine_ops)
    WHERE dimension = 1024 AND active = TRUE;
CREATE INDEX idx_chunk_embedding_1536_hnsw ON knowledge_chunk_embedding
    USING hnsw ((embedding::vector(1536)) vector_cosine_ops)
    WHERE dimension = 1536 AND active = TRUE;
CREATE INDEX idx_chunk_embedding_space_profile
    ON knowledge_chunk_embedding(tenant_id, space_id, profile_code, active);
