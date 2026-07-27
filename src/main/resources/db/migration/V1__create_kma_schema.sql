CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_dataset (
    dataset_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    chunk_strategy JSONB,
    parse_config JSONB,
    rerank_enabled BOOLEAN NOT NULL DEFAULT true,
    rerank_model VARCHAR(64),
    preset_questions JSONB,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);
CREATE INDEX idx_dataset_tenant_status ON knowledge_dataset(tenant_id, status);

CREATE TABLE knowledge_space (
    space_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    dataset_id BIGINT REFERENCES knowledge_dataset(dataset_id) ON DELETE SET NULL,
    space_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    embedding_provider VARCHAR(32) NOT NULL,
    embedding_model VARCHAR(64) NOT NULL,
    embedding_dim INT NOT NULL,
    distance_metric VARCHAR(16) NOT NULL DEFAULT 'cosine',
    chunk_strategy JSONB,
    default_top_k INT NOT NULL DEFAULT 5,
    score_threshold NUMERIC(5,3) NOT NULL DEFAULT 0.35,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, space_code)
);
CREATE INDEX idx_space_tenant_dataset ON knowledge_space(tenant_id, dataset_id);
CREATE INDEX idx_space_tenant_status ON knowledge_space(tenant_id, status);

CREATE TABLE knowledge_doc (
    doc_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    space_id BIGINT NOT NULL REFERENCES knowledge_space(space_id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    source_tag VARCHAR(64),
    external_ref VARCHAR(256),
    source_version BIGINT NOT NULL DEFAULT 1,
    mime_type VARCHAR(128),
    storage_path VARCHAR(512),
    content_hash VARCHAR(64),
    parse_status VARCHAR(16) NOT NULL DEFAULT 'pending',
    chunk_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    meta JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_doc_tenant_space ON knowledge_doc(tenant_id, space_id);
CREATE INDEX idx_doc_tenant_source ON knowledge_doc(tenant_id, space_id, source_tag);
CREATE INDEX idx_doc_tenant_hash ON knowledge_doc(tenant_id, content_hash);
CREATE INDEX idx_doc_tenant_status ON knowledge_doc(tenant_id, parse_status);
CREATE UNIQUE INDEX uk_doc_tenant_external_ref
    ON knowledge_doc(tenant_id, space_id, external_ref) WHERE external_ref IS NOT NULL;

CREATE TABLE knowledge_chunk (
    chunk_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    doc_id BIGINT NOT NULL REFERENCES knowledge_doc(doc_id) ON DELETE CASCADE,
    space_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    char_offset INT,
    token_count INT,
    source_tag VARCHAR(64),
    embedding VECTOR(1024),
    full_text_vector TSVECTOR GENERATED ALWAYS AS
        (to_tsvector('simple', COALESCE(content, ''))) STORED,
    embedding_model VARCHAR(64),
    meta JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, doc_id, chunk_index)
);
CREATE INDEX idx_chunk_tenant_space ON knowledge_chunk(tenant_id, space_id);
CREATE INDEX idx_chunk_tenant_doc ON knowledge_chunk(tenant_id, doc_id);
CREATE INDEX idx_chunk_embedding_hnsw ON knowledge_chunk
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_chunk_full_text_gin ON knowledge_chunk USING gin (full_text_vector);

CREATE TABLE knowledge_space_acl (
    acl_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    space_id BIGINT NOT NULL REFERENCES knowledge_space(space_id) ON DELETE CASCADE,
    principal_type VARCHAR(16) NOT NULL,
    principal_value VARCHAR(128) NOT NULL,
    permission VARCHAR(16) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, space_id, principal_type, principal_value, permission)
);
CREATE INDEX idx_acl_tenant_space ON knowledge_space_acl(tenant_id, space_id);

CREATE TABLE knowledge_call_log (
    log_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT,
    username VARCHAR(128),
    space_code VARCHAR(64) NOT NULL,
    rag_mode VARCHAR(16) NOT NULL,
    query TEXT,
    top_k INT,
    source_tags VARCHAR(512),
    hit_count INT,
    prompt_tokens INT,
    completion_tokens INT,
    cost_millis INT,
    llm_model VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_call_log_tenant_user ON knowledge_call_log(tenant_id, user_id);
CREATE INDEX idx_call_log_tenant_space_time ON knowledge_call_log(tenant_id, space_code, create_time DESC);

CREATE TABLE knowledge_chat_session (
    session_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT,
    space_code VARCHAR(64) NOT NULL,
    title VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_session_tenant_user ON knowledge_chat_session(tenant_id, user_id);
CREATE INDEX idx_chat_session_tenant_space_time
    ON knowledge_chat_session(tenant_id, space_code, update_time DESC);

CREATE TABLE knowledge_chat_message (
    message_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    session_id BIGINT NOT NULL REFERENCES knowledge_chat_session(session_id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    citations JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_message_tenant_session
    ON knowledge_chat_message(tenant_id, session_id, create_time);

CREATE TABLE knowledge_feed_task (
    task_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    source_version_id BIGINT,
    space_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 3,
    next_execute_time TIMESTAMP NOT NULL DEFAULT now(),
    error_message VARCHAR(500),
    meta JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, source_type, source_id, source_version_id, space_code)
);
CREATE INDEX idx_feed_task_tenant_status_time
    ON knowledge_feed_task(tenant_id, status, next_execute_time);

COMMENT ON TABLE knowledge_space IS 'KMA tenant-isolated knowledge space';
COMMENT ON TABLE knowledge_doc IS 'KMA idempotent source document';
COMMENT ON TABLE knowledge_chunk IS 'KMA vector and full-text searchable chunk';
