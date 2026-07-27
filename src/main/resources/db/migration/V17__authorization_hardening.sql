-- KMA-AUTH.2：权限、ACL 与租户关系的数据库终态约束。
-- 安全原则：发现历史跨租户关系时停止迁移，禁止自动猜测数据归属。

DO $$
DECLARE
    tenant_mismatches BIGINT;
    missing_principals BIGINT;
    spaces_without_admin BIGINT;
BEGIN
    SELECT COALESCE(sum(cnt), 0) INTO tenant_mismatches
    FROM (
        SELECT count(*) cnt FROM knowledge_space c JOIN knowledge_dataset p ON p.dataset_id=c.dataset_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_space_acl c JOIN knowledge_space p ON p.space_id=c.space_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_doc c JOIN knowledge_space p ON p.space_id=c.space_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_doc c JOIN knowledge_doc p ON p.doc_id=c.supersedes_doc_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_chunk c JOIN knowledge_doc p ON p.doc_id=c.doc_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_chunk c JOIN knowledge_space p ON p.space_id=c.space_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_chunk_embedding c JOIN knowledge_chunk p ON p.chunk_id=c.chunk_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_chunk_embedding c JOIN knowledge_space p ON p.space_id=c.space_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_ingestion_job c JOIN knowledge_doc p ON p.doc_id=c.doc_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM knowledge_chat_message c JOIN knowledge_chat_session p ON p.session_id=c.session_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM kma_embedding_rebuild_job c JOIN knowledge_dataset p ON p.dataset_id=c.dataset_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM kma_evaluation_case c JOIN kma_evaluation_dataset p ON p.evaluation_dataset_id=c.evaluation_dataset_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM kma_evaluation_run c JOIN kma_evaluation_dataset p ON p.evaluation_dataset_id=c.evaluation_dataset_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM kma_evaluation_gate c JOIN kma_evaluation_dataset p ON p.evaluation_dataset_id=c.evaluation_dataset_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM kma_evaluation_result c JOIN kma_evaluation_run p ON p.evaluation_run_id=c.evaluation_run_id WHERE c.tenant_id<>p.tenant_id
        UNION ALL SELECT count(*) FROM kma_evaluation_result c JOIN kma_evaluation_case p ON p.evaluation_case_id=c.evaluation_case_id WHERE c.tenant_id<>p.tenant_id
    ) mismatches;
    IF tenant_mismatches > 0 THEN
        RAISE EXCEPTION 'V17_TENANT_RELATION_MISMATCH: % invalid relations; run the authorization preflight report before retrying', tenant_mismatches;
    END IF;

    SELECT count(*) INTO missing_principals
    FROM knowledge_space_acl a
    WHERE (a.principal_type='user' AND (a.principal_value !~ '^[0-9]+$' OR NOT EXISTS (
              SELECT 1 FROM kma_user u WHERE u.tenant_id=a.tenant_id AND u.user_id::text=a.principal_value)))
       OR (a.principal_type='role' AND NOT EXISTS (
              SELECT 1 FROM kma_role r WHERE r.tenant_id=a.tenant_id AND r.role_code=a.principal_value))
       OR (a.principal_type='org' AND NOT EXISTS (
              SELECT 1 FROM kma_org o WHERE o.tenant_id=a.tenant_id AND o.org_code=a.principal_value));
    IF missing_principals > 0 THEN
        RAISE EXCEPTION 'V17_ACL_PRINCIPAL_MISSING: % ACL rows reference missing principals', missing_principals;
    END IF;

    SELECT count(*) INTO spaces_without_admin
    FROM knowledge_space s
    WHERE NOT EXISTS (
        SELECT 1 FROM knowledge_space_acl a
        WHERE a.tenant_id=s.tenant_id AND a.space_id=s.space_id AND a.permission='admin'
          AND CASE a.principal_type
              WHEN 'user' THEN a.principal_value ~ '^[0-9]+$' AND EXISTS (
                  SELECT 1 FROM kma_user u WHERE u.tenant_id=a.tenant_id
                    AND u.user_id::text=a.principal_value AND u.status='active')
              WHEN 'role' THEN EXISTS (
                  SELECT 1 FROM kma_role r WHERE r.tenant_id=a.tenant_id
                    AND r.role_code=a.principal_value AND r.status='active')
              WHEN 'org' THEN EXISTS (
                  SELECT 1 FROM kma_org o WHERE o.tenant_id=a.tenant_id
                    AND o.org_code=a.principal_value AND o.status='active')
              ELSE FALSE END
    );
    IF spaces_without_admin > 0 THEN
        RAISE EXCEPTION 'V17_LAST_EFFECTIVE_SPACE_ADMIN_REQUIRED: % spaces have no active admin principal', spaces_without_admin;
    END IF;
END $$;

-- 复合外键的父表唯一键。
ALTER TABLE knowledge_dataset ADD CONSTRAINT uk_dataset_tenant_id UNIQUE (tenant_id,dataset_id);
ALTER TABLE knowledge_space ADD CONSTRAINT uk_space_tenant_id UNIQUE (tenant_id,space_id);
ALTER TABLE knowledge_chunk ADD CONSTRAINT uk_chunk_tenant_id UNIQUE (tenant_id,chunk_id);
ALTER TABLE knowledge_chat_session ADD CONSTRAINT uk_chat_session_tenant_id UNIQUE (tenant_id,session_id);
ALTER TABLE kma_evaluation_dataset ADD CONSTRAINT uk_eval_dataset_tenant_id UNIQUE (tenant_id,evaluation_dataset_id);
ALTER TABLE kma_evaluation_case ADD CONSTRAINT uk_eval_case_tenant_id UNIQUE (tenant_id,evaluation_case_id);
ALTER TABLE kma_evaluation_run ADD CONSTRAINT uk_eval_run_tenant_id UNIQUE (tenant_id,evaluation_run_id);

-- 所有租户知识表必须引用一个真实租户。
ALTER TABLE knowledge_dataset ADD CONSTRAINT fk_dataset_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);
ALTER TABLE knowledge_space ADD CONSTRAINT fk_space_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);
ALTER TABLE knowledge_space_acl ADD CONSTRAINT fk_space_acl_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);
ALTER TABLE knowledge_doc ADD CONSTRAINT fk_doc_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);
ALTER TABLE knowledge_chunk ADD CONSTRAINT fk_chunk_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);
ALTER TABLE knowledge_chunk_embedding ADD CONSTRAINT fk_chunk_embedding_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);
ALTER TABLE knowledge_ingestion_job ADD CONSTRAINT fk_ingestion_job_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);
ALTER TABLE knowledge_chat_session ADD CONSTRAINT fk_chat_session_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);
ALTER TABLE knowledge_chat_message ADD CONSTRAINT fk_chat_message_tenant FOREIGN KEY (tenant_id) REFERENCES kma_tenant(tenant_id);

-- 用复合外键替换仅按全局 ID 关联的外键；可空关系只清空 ID，不清空 tenant_id。
ALTER TABLE knowledge_space
    DROP CONSTRAINT knowledge_space_dataset_id_fkey,
    ADD CONSTRAINT fk_space_dataset_tenant FOREIGN KEY (tenant_id,dataset_id)
        REFERENCES knowledge_dataset(tenant_id,dataset_id) ON DELETE SET NULL (dataset_id);
ALTER TABLE knowledge_space_acl
    DROP CONSTRAINT knowledge_space_acl_space_id_fkey,
    ADD CONSTRAINT fk_space_acl_space_tenant FOREIGN KEY (tenant_id,space_id)
        REFERENCES knowledge_space(tenant_id,space_id) ON DELETE CASCADE;
ALTER TABLE knowledge_doc
    DROP CONSTRAINT knowledge_doc_space_id_fkey,
    DROP CONSTRAINT knowledge_doc_supersedes_doc_id_fkey,
    ADD CONSTRAINT fk_doc_space_tenant FOREIGN KEY (tenant_id,space_id)
        REFERENCES knowledge_space(tenant_id,space_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_doc_supersedes_tenant FOREIGN KEY (tenant_id,supersedes_doc_id)
        REFERENCES knowledge_doc(tenant_id,doc_id) ON DELETE SET NULL (supersedes_doc_id);
ALTER TABLE knowledge_chunk
    DROP CONSTRAINT knowledge_chunk_doc_id_fkey,
    ADD CONSTRAINT fk_chunk_doc_tenant FOREIGN KEY (tenant_id,doc_id)
        REFERENCES knowledge_doc(tenant_id,doc_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_chunk_space_tenant FOREIGN KEY (tenant_id,space_id)
        REFERENCES knowledge_space(tenant_id,space_id) ON DELETE CASCADE;
ALTER TABLE knowledge_chunk_embedding
    DROP CONSTRAINT knowledge_chunk_embedding_chunk_id_fkey,
    ADD CONSTRAINT fk_chunk_embedding_chunk_tenant FOREIGN KEY (tenant_id,chunk_id)
        REFERENCES knowledge_chunk(tenant_id,chunk_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_chunk_embedding_space_tenant FOREIGN KEY (tenant_id,space_id)
        REFERENCES knowledge_space(tenant_id,space_id) ON DELETE CASCADE;
ALTER TABLE knowledge_ingestion_job
    DROP CONSTRAINT knowledge_ingestion_job_doc_id_fkey,
    ADD CONSTRAINT fk_ingestion_job_doc_tenant FOREIGN KEY (tenant_id,doc_id)
        REFERENCES knowledge_doc(tenant_id,doc_id) ON DELETE CASCADE;
ALTER TABLE knowledge_chat_message
    DROP CONSTRAINT knowledge_chat_message_session_id_fkey,
    ADD CONSTRAINT fk_chat_message_session_tenant FOREIGN KEY (tenant_id,session_id)
        REFERENCES knowledge_chat_session(tenant_id,session_id) ON DELETE CASCADE;
ALTER TABLE kma_embedding_rebuild_job
    DROP CONSTRAINT kma_embedding_rebuild_job_dataset_id_fkey,
    ADD CONSTRAINT fk_rebuild_dataset_tenant FOREIGN KEY (tenant_id,dataset_id)
        REFERENCES knowledge_dataset(tenant_id,dataset_id);
ALTER TABLE kma_evaluation_case
    DROP CONSTRAINT kma_evaluation_case_evaluation_dataset_id_fkey,
    ADD CONSTRAINT fk_eval_case_dataset_tenant FOREIGN KEY (tenant_id,evaluation_dataset_id)
        REFERENCES kma_evaluation_dataset(tenant_id,evaluation_dataset_id) ON DELETE CASCADE;
ALTER TABLE kma_evaluation_run
    DROP CONSTRAINT kma_evaluation_run_evaluation_dataset_id_fkey,
    ADD CONSTRAINT fk_eval_run_dataset_tenant FOREIGN KEY (tenant_id,evaluation_dataset_id)
        REFERENCES kma_evaluation_dataset(tenant_id,evaluation_dataset_id) ON DELETE CASCADE;
ALTER TABLE kma_evaluation_gate
    DROP CONSTRAINT kma_evaluation_gate_evaluation_dataset_id_fkey,
    ADD CONSTRAINT fk_eval_gate_dataset_tenant FOREIGN KEY (tenant_id,evaluation_dataset_id)
        REFERENCES kma_evaluation_dataset(tenant_id,evaluation_dataset_id) ON DELETE CASCADE;
ALTER TABLE kma_evaluation_result
    DROP CONSTRAINT kma_evaluation_result_evaluation_run_id_fkey,
    DROP CONSTRAINT kma_evaluation_result_evaluation_case_id_fkey,
    ADD CONSTRAINT fk_eval_result_run_tenant FOREIGN KEY (tenant_id,evaluation_run_id)
        REFERENCES kma_evaluation_run(tenant_id,evaluation_run_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_eval_result_case_tenant FOREIGN KEY (tenant_id,evaluation_case_id)
        REFERENCES kma_evaluation_case(tenant_id,evaluation_case_id) ON DELETE CASCADE;

-- 刷新令牌族用于严格轮换和重放检测。
ALTER TABLE kma_refresh_token
    ADD COLUMN family_id UUID,
    ADD COLUMN used_at TIMESTAMP,
    ADD COLUMN reuse_detected_at TIMESTAMP;
UPDATE kma_refresh_token SET family_id=token_id WHERE family_id IS NULL;
ALTER TABLE kma_refresh_token ALTER COLUMN family_id SET NOT NULL;
CREATE INDEX idx_refresh_token_family ON kma_refresh_token(family_id,user_id);

-- 权限变更审计需要可关联、可比较，但仍不保存密钥和原始令牌。
ALTER TABLE kma_security_audit
    ADD COLUMN trace_id VARCHAR(64),
    ADD COLUMN actor_token_source VARCHAR(16),
    ADD COLUMN target_type VARCHAR(32),
    ADD COLUMN target_id VARCHAR(128),
    ADD COLUMN before_state JSONB,
    ADD COLUMN after_state JSONB;
CREATE INDEX idx_security_audit_trace ON kma_security_audit(trace_id) WHERE trace_id IS NOT NULL;

CREATE OR REPLACE FUNCTION kma_acl_principal_is_active(
    p_tenant VARCHAR, p_type VARCHAR, p_value VARCHAR
) RETURNS BOOLEAN
LANGUAGE plpgsql STABLE AS $$
BEGIN
    RETURN CASE p_type
        WHEN 'user' THEN p_value ~ '^[0-9]+$' AND EXISTS (
            SELECT 1 FROM kma_user u WHERE u.tenant_id=p_tenant
              AND u.user_id::text=p_value AND u.status='active')
        WHEN 'role' THEN EXISTS (
            SELECT 1 FROM kma_role r WHERE r.tenant_id=p_tenant
              AND r.role_code=p_value AND r.status='active')
        WHEN 'org' THEN EXISTS (
            SELECT 1 FROM kma_org o WHERE o.tenant_id=p_tenant
              AND o.org_code=p_value AND o.status='active')
        ELSE FALSE END;
END $$;

CREATE OR REPLACE FUNCTION kma_validate_acl_principal() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT kma_acl_principal_is_active(NEW.tenant_id,NEW.principal_type,NEW.principal_value) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='ACL_PRINCIPAL_INVALID_OR_INACTIVE';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_acl_validate_principal
    BEFORE INSERT OR UPDATE OF tenant_id,principal_type,principal_value,permission
    ON knowledge_space_acl FOR EACH ROW EXECUTE FUNCTION kma_validate_acl_principal();

CREATE OR REPLACE FUNCTION kma_assert_space_active_admin(p_tenant VARCHAR,p_space BIGINT) RETURNS VOID
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(p_tenant || ':' || p_space::text,0));
    IF EXISTS (SELECT 1 FROM knowledge_space s WHERE s.tenant_id=p_tenant AND s.space_id=p_space)
       AND NOT EXISTS (
           SELECT 1 FROM knowledge_space_acl a
           WHERE a.tenant_id=p_tenant AND a.space_id=p_space AND a.permission='admin'
             AND kma_acl_principal_is_active(a.tenant_id,a.principal_type,a.principal_value)
       ) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='LAST_EFFECTIVE_SPACE_ADMIN_REQUIRED';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION kma_acl_admin_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.permission='admin' THEN
        PERFORM kma_assert_space_active_admin(OLD.tenant_id,OLD.space_id);
    END IF;
    RETURN OLD;
END $$;
CREATE CONSTRAINT TRIGGER ctr_acl_last_admin
    AFTER DELETE OR UPDATE ON knowledge_space_acl
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION kma_acl_admin_guard();

CREATE OR REPLACE FUNCTION kma_assert_principal_admin_spaces(
    p_tenant VARCHAR,p_type VARCHAR,p_value VARCHAR
) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE target RECORD;
BEGIN
    FOR target IN
        SELECT DISTINCT a.space_id FROM knowledge_space_acl a
        WHERE a.tenant_id=p_tenant AND a.principal_type=p_type
          AND a.principal_value=p_value AND a.permission='admin'
        ORDER BY a.space_id
    LOOP
        PERFORM kma_assert_space_active_admin(p_tenant,target.space_id);
    END LOOP;
END $$;

CREATE OR REPLACE FUNCTION kma_acl_user_status_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF EXISTS (SELECT 1 FROM knowledge_space_acl a WHERE a.tenant_id=OLD.tenant_id
                   AND a.principal_type='user' AND a.principal_value=OLD.user_id::text) THEN
            RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='ACL_PRINCIPAL_IN_USE';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status='active' AND NEW.status<>'active' THEN
        PERFORM kma_assert_principal_admin_spaces(OLD.tenant_id,'user',OLD.user_id::text);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_acl_user_delete_guard BEFORE DELETE ON kma_user
    FOR EACH ROW EXECUTE FUNCTION kma_acl_user_status_guard();
CREATE CONSTRAINT TRIGGER ctr_acl_user_status_guard AFTER UPDATE ON kma_user
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION kma_acl_user_status_guard();

CREATE OR REPLACE FUNCTION kma_acl_role_status_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF EXISTS (SELECT 1 FROM knowledge_space_acl a WHERE a.tenant_id=OLD.tenant_id
                   AND a.principal_type='role' AND a.principal_value=OLD.role_code) THEN
            RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='ACL_PRINCIPAL_IN_USE';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status='active' AND NEW.status<>'active' THEN
        PERFORM kma_assert_principal_admin_spaces(OLD.tenant_id,'role',OLD.role_code);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_acl_role_delete_guard BEFORE DELETE ON kma_role
    FOR EACH ROW EXECUTE FUNCTION kma_acl_role_status_guard();
CREATE CONSTRAINT TRIGGER ctr_acl_role_status_guard AFTER UPDATE ON kma_role
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION kma_acl_role_status_guard();

CREATE OR REPLACE FUNCTION kma_acl_org_status_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF EXISTS (SELECT 1 FROM knowledge_space_acl a WHERE a.tenant_id=OLD.tenant_id
                   AND a.principal_type='org' AND a.principal_value=OLD.org_code) THEN
            RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='ACL_PRINCIPAL_IN_USE';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status='active' AND NEW.status<>'active' THEN
        PERFORM kma_assert_principal_admin_spaces(OLD.tenant_id,'org',OLD.org_code);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_acl_org_delete_guard BEFORE DELETE ON kma_org
    FOR EACH ROW EXECUTE FUNCTION kma_acl_org_status_guard();
CREATE CONSTRAINT TRIGGER ctr_acl_org_status_guard AFTER UPDATE ON kma_org
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION kma_acl_org_status_guard();
