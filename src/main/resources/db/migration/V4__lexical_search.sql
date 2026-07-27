ALTER TABLE knowledge_chunk
    ADD COLUMN search_text TEXT,
    ADD COLUMN search_vector TSVECTOR GENERATED ALWAYS AS
        (to_tsvector('simple', COALESCE(search_text, ''))) STORED;

UPDATE knowledge_chunk SET search_text = content WHERE search_text IS NULL;

CREATE INDEX idx_chunk_search_vector_gin
    ON knowledge_chunk USING gin (search_vector);

COMMENT ON COLUMN knowledge_chunk.search_text IS
    'Application-tokenized lexical text; Chinese content is indexed as bigrams';
