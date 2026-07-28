-- Governance completion: publication timing, optional separation of duties,
-- answer feedback and a lightweight operational quality read model.

ALTER TABLE knowledge_doc
    ADD COLUMN IF NOT EXISTS scheduled_online_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS scheduled_offline_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS schedule_note VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES kma_user(user_id) ON DELETE SET NULL;

ALTER TABLE knowledge_doc
    ADD CONSTRAINT ck_doc_schedule_window CHECK (
        scheduled_offline_at IS NULL OR scheduled_online_at IS NULL
        OR scheduled_offline_at > scheduled_online_at
    );

CREATE INDEX IF NOT EXISTS idx_doc_scheduled_visibility
    ON knowledge_doc(scheduled_online_at,scheduled_offline_at)
    WHERE publication_managed AND (scheduled_online_at IS NOT NULL OR scheduled_offline_at IS NOT NULL);

CREATE TABLE IF NOT EXISTS kma_governance_policy (
    policy_key VARCHAR(64) PRIMARY KEY,
    content_separation_of_duties BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BIGINT,
    update_time TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO kma_governance_policy(policy_key,content_separation_of_duties)
VALUES ('default',FALSE) ON CONFLICT (policy_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS knowledge_qa_feedback (
    feedback_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES kma_user(user_id) ON DELETE CASCADE,
    space_code VARCHAR(64),
    session_id BIGINT REFERENCES knowledge_chat_session(session_id) ON DELETE SET NULL,
    rating VARCHAR(16) NOT NULL CHECK (rating IN ('helpful','unhelpful')),
    reason VARCHAR(64),
    comment VARCHAR(1000),
    question VARCHAR(2000),
    answer_excerpt VARCHAR(4000),
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    evaluation_case_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    converted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_qa_feedback_governance
    ON knowledge_qa_feedback(rating,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_qa_feedback_session
    ON knowledge_qa_feedback(session_id,created_at DESC);
