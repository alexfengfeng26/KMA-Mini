-- KMA Mini is a physically single-tenant application from this version onward.
-- V1-V21 remain immutable so existing installations can upgrade safely.

DO $$
DECLARE
    item RECORD;
    non_default_rows BIGINT;
BEGIN
    FOR item IN
        SELECT table_schema, table_name
        FROM information_schema.columns
        WHERE table_schema = 'public' AND column_name = 'tenant_id'
        ORDER BY table_name
    LOOP
        EXECUTE format(
            'SELECT count(*) FROM %I.%I WHERE tenant_id IS DISTINCT FROM %L',
            item.table_schema, item.table_name, 'default'
        ) INTO non_default_rows;
        IF non_default_rows > 0 THEN
            RAISE EXCEPTION
                'V22 single-tenant cleanup refused: %.% contains % non-default tenant rows',
                item.table_schema, item.table_name, non_default_rows;
        END IF;
    END LOOP;
END $$;

-- Preserve the effective administrator before removing the legacy tenant role.
INSERT INTO kma_role (
    tenant_id, role_code, name, built_in, create_time, update_time, description, status
)
SELECT
    legacy.tenant_id,
    'kma-admin',
    'KMA 管理员',
    TRUE,
    legacy.create_time,
    legacy.update_time,
    'KMA Mini 全部管理权限',
    legacy.status
FROM kma_role legacy
WHERE legacy.role_code = 'tenant-admin'
  AND NOT EXISTS (
      SELECT 1
      FROM kma_role target
      WHERE target.tenant_id = legacy.tenant_id
        AND target.role_code = 'kma-admin'
  );

INSERT INTO kma_role_permission (tenant_id, role_id, permission_code)
SELECT legacy.tenant_id, target.role_id, rp.permission_code
FROM kma_role_permission rp
JOIN kma_role legacy
  ON legacy.tenant_id = rp.tenant_id
 AND legacy.role_id = rp.role_id
 AND legacy.role_code = 'tenant-admin'
JOIN kma_role target
  ON target.tenant_id = legacy.tenant_id
 AND target.role_code = 'kma-admin'
ON CONFLICT DO NOTHING;

INSERT INTO kma_user_role (tenant_id, user_id, role_id)
SELECT ur.tenant_id, ur.user_id, target.role_id
FROM kma_user_role ur
JOIN kma_role legacy
  ON legacy.tenant_id = ur.tenant_id
 AND legacy.role_id = ur.role_id
 AND legacy.role_code = 'tenant-admin'
JOIN kma_role target
  ON target.tenant_id = ur.tenant_id
 AND target.role_code = 'kma-admin'
ON CONFLICT DO NOTHING;

UPDATE knowledge_space_acl
SET principal_value = 'kma-admin'
WHERE principal_type = 'role' AND principal_value = 'tenant-admin';

DELETE FROM kma_user_role ur
USING kma_role r
WHERE ur.tenant_id = r.tenant_id
  AND ur.role_id = r.role_id
  AND r.role_code = 'tenant-admin';

DELETE FROM kma_role_permission rp
USING kma_role r
WHERE rp.tenant_id = r.tenant_id
  AND rp.role_id = r.role_id
  AND r.role_code = 'tenant-admin';

DELETE FROM kma_role WHERE role_code = 'tenant-admin';

DELETE FROM kma_role_permission
WHERE permission_code IN (
    'tenant:create', 'tenant:read', 'tenant:update', 'tenant:manage',
    'quota:read', 'quota:update', 'quota:manage'
);

DELETE FROM kma_permission
WHERE permission_code IN (
    'tenant:create', 'tenant:read', 'tenant:update', 'tenant:manage',
    'quota:read', 'quota:update', 'quota:manage'
);

-- Old tokens contain tenant claims and must never survive the contract change.
DELETE FROM kma_refresh_token;

-- Site-owned extension packages replace the former tenant-owned vocabulary.
UPDATE knowledge_portal_config_version
SET config_json = replace(
    replace(config_json::text, '"source": "tenant"', '"source": "site"'),
    '"source":"tenant"', '"source":"site"'
)::jsonb
WHERE config_json::text LIKE '%"source"%tenant%';

UPDATE knowledge_portal_site
SET name = 'KMA Mini', update_time = now()
WHERE name = '默认租户';

UPDATE knowledge_portal_config
SET unit_name = 'KMA Mini', update_time = now()
WHERE unit_name = '默认租户';

UPDATE knowledge_portal_config_version
SET config_json =
    jsonb_set(config_json, '{site,name}', '"KMA Mini"'::jsonb, FALSE)
    #- '{shell,header,showTenant}'
WHERE config_json #>> '{site,name}' = '默认租户'
   OR config_json #> '{shell,header}' ? 'showTenant';

-- Flush deferred foreign-key trigger work before changing constraint definitions.
SET CONSTRAINTS ALL IMMEDIATE;

-- The portal configuration remains a singleton after its tenant primary key is removed.
ALTER TABLE knowledge_portal_config
    ADD COLUMN config_id SMALLINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_knowledge_portal_config_singleton CHECK (config_id = 1);

DROP TRIGGER trg_acl_validate_principal ON knowledge_space_acl;
DROP TRIGGER ctr_acl_last_admin ON knowledge_space_acl;
DROP TRIGGER ctr_acl_org_status_guard ON kma_org;
DROP TRIGGER trg_acl_org_delete_guard ON kma_org;
DROP TRIGGER ctr_acl_role_status_guard ON kma_role;
DROP TRIGGER trg_acl_role_delete_guard ON kma_role;
DROP TRIGGER ctr_acl_user_status_guard ON kma_user;
DROP TRIGGER trg_acl_user_delete_guard ON kma_user;

DROP FUNCTION kma_acl_admin_guard();
DROP FUNCTION kma_acl_org_status_guard();
DROP FUNCTION kma_acl_role_status_guard();
DROP FUNCTION kma_acl_user_status_guard();
DROP FUNCTION kma_validate_acl_principal();
DROP FUNCTION kma_assert_principal_admin_spaces(VARCHAR, VARCHAR, VARCHAR);
DROP FUNCTION kma_assert_space_active_admin(VARCHAR, BIGINT);
DROP FUNCTION kma_acl_principal_is_active(VARCHAR, VARCHAR, VARCHAR);

CREATE TEMP TABLE kma_v22_constraints (
    table_schema TEXT NOT NULL,
    table_name TEXT NOT NULL,
    constraint_name TEXT NOT NULL,
    constraint_type "char" NOT NULL,
    replacement_definition TEXT
) ON COMMIT DROP;

INSERT INTO kma_v22_constraints
SELECT
    n.nspname,
    c.relname,
    con.conname,
    con.contype,
    CASE
        WHEN con.contype NOT IN ('p', 'u', 'f') THEN NULL
        WHEN ref.relname = 'kma_tenant' THEN NULL
        ELSE replace(
                 replace(pg_get_constraintdef(con.oid), 'tenant_id, ', ''),
                 ', tenant_id', ''
             )
    END
FROM pg_constraint con
JOIN pg_class c ON c.oid = con.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
LEFT JOIN pg_class ref ON ref.oid = con.confrelid
WHERE n.nspname = 'public'
  AND con.contype IN ('p', 'u', 'f')
  AND (
      pg_get_constraintdef(con.oid) ILIKE '%tenant_id%'
      OR c.relname IN ('kma_tenant_quota', 'kma_tenant_daily_usage')
  );

UPDATE kma_v22_constraints
SET replacement_definition = NULL
WHERE replacement_definition LIKE '%()%'
   OR replacement_definition ILIKE '%(tenant_id)%'
   OR table_name IN ('kma_tenant', 'kma_tenant_quota', 'kma_tenant_daily_usage');

CREATE TEMP TABLE kma_v22_indexes (
    index_schema TEXT NOT NULL,
    index_name TEXT NOT NULL,
    replacement_definition TEXT
) ON COMMIT DROP;

INSERT INTO kma_v22_indexes
SELECT
    ni.nspname,
    idx.relname,
    replace(
        replace(pg_get_indexdef(i.indexrelid), 'tenant_id, ', ''),
        ', tenant_id', ''
    )
FROM pg_index i
JOIN pg_class tbl ON tbl.oid = i.indrelid
JOIN pg_namespace nt ON nt.oid = tbl.relnamespace
JOIN pg_class idx ON idx.oid = i.indexrelid
JOIN pg_namespace ni ON ni.oid = idx.relnamespace
LEFT JOIN pg_constraint con ON con.conindid = i.indexrelid
WHERE nt.nspname = 'public'
  AND con.oid IS NULL
  AND pg_get_indexdef(i.indexrelid) ILIKE '%tenant_id%'
  AND tbl.relname NOT IN ('kma_tenant', 'kma_tenant_quota', 'kma_tenant_daily_usage');

UPDATE kma_v22_indexes
SET replacement_definition = NULL
WHERE replacement_definition LIKE '%()%'
   OR replacement_definition ILIKE '%(tenant_id)%';

DO $$
DECLARE item RECORD;
BEGIN
    FOR item IN
        SELECT * FROM kma_v22_constraints WHERE constraint_type = 'f'
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            item.table_schema, item.table_name, item.constraint_name
        );
    END LOOP;

    FOR item IN
        SELECT * FROM kma_v22_constraints WHERE constraint_type <> 'f'
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            item.table_schema, item.table_name, item.constraint_name
        );
    END LOOP;

    FOR item IN SELECT * FROM kma_v22_indexes
    LOOP
        EXECUTE format('DROP INDEX %I.%I', item.index_schema, item.index_name);
    END LOOP;
END $$;

DO $$
DECLARE item RECORD;
BEGIN
    FOR item IN
        SELECT table_schema, table_name
        FROM information_schema.columns
        WHERE table_schema = 'public' AND column_name = 'tenant_id'
          AND table_name NOT IN ('kma_tenant', 'kma_tenant_quota', 'kma_tenant_daily_usage')
        ORDER BY table_name
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP COLUMN tenant_id',
            item.table_schema, item.table_name
        );
    END LOOP;
END $$;

ALTER TABLE kma_permission DROP CONSTRAINT IF EXISTS ck_kma_permission_scope;
ALTER TABLE kma_permission DROP COLUMN permission_scope;

DROP TABLE kma_tenant_daily_usage;
DROP TABLE kma_tenant_quota;
DROP TABLE kma_tenant;

DO $$
DECLARE item RECORD;
BEGIN
    FOR item IN
        SELECT * FROM kma_v22_constraints
        WHERE replacement_definition IS NOT NULL
          AND constraint_type IN ('p', 'u')
        ORDER BY CASE constraint_type WHEN 'p' THEN 0 ELSE 1 END, table_name, constraint_name
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I %s',
            item.table_schema, item.table_name, item.constraint_name, item.replacement_definition
        );
    END LOOP;

    ALTER TABLE knowledge_portal_config
        ADD CONSTRAINT knowledge_portal_config_pkey PRIMARY KEY (config_id);

    FOR item IN
        SELECT * FROM kma_v22_constraints
        WHERE replacement_definition IS NOT NULL AND constraint_type = 'f'
        ORDER BY table_name, constraint_name
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I %s',
            item.table_schema, item.table_name, item.constraint_name, item.replacement_definition
        );
    END LOOP;

    FOR item IN
        SELECT * FROM kma_v22_indexes
        WHERE replacement_definition IS NOT NULL
        ORDER BY index_name
    LOOP
        EXECUTE item.replacement_definition;
    END LOOP;
END $$;

CREATE FUNCTION kma_acl_principal_is_active(p_type VARCHAR, p_value VARCHAR)
RETURNS BOOLEAN LANGUAGE plpgsql STABLE AS $$
BEGIN
    RETURN CASE p_type
        WHEN 'user' THEN p_value ~ '^[0-9]+$' AND EXISTS (
            SELECT 1 FROM kma_user u
            WHERE u.user_id::text = p_value AND u.status = 'active')
        WHEN 'role' THEN EXISTS (
            SELECT 1 FROM kma_role r
            WHERE r.role_code = p_value AND r.status = 'active')
        WHEN 'org' THEN EXISTS (
            SELECT 1 FROM kma_org o
            WHERE o.org_code = p_value AND o.status = 'active')
        ELSE FALSE
    END;
END $$;

CREATE FUNCTION kma_assert_space_active_admin(p_space BIGINT)
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(p_space);
    IF EXISTS (SELECT 1 FROM knowledge_space s WHERE s.space_id = p_space)
       AND NOT EXISTS (
           SELECT 1 FROM knowledge_space_acl a
           WHERE a.space_id = p_space AND a.permission = 'admin'
             AND kma_acl_principal_is_active(a.principal_type, a.principal_value)
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'LAST_EFFECTIVE_SPACE_ADMIN_REQUIRED';
    END IF;
END $$;

CREATE FUNCTION kma_assert_principal_admin_spaces(p_type VARCHAR, p_value VARCHAR)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE target RECORD;
BEGIN
    FOR target IN
        SELECT DISTINCT a.space_id
        FROM knowledge_space_acl a
        WHERE a.principal_type = p_type
          AND a.principal_value = p_value
          AND a.permission = 'admin'
        ORDER BY a.space_id
    LOOP
        PERFORM kma_assert_space_active_admin(target.space_id);
    END LOOP;
END $$;

CREATE FUNCTION kma_validate_acl_principal()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT kma_acl_principal_is_active(NEW.principal_type, NEW.principal_value) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'ACL_PRINCIPAL_INVALID_OR_INACTIVE';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION kma_acl_admin_guard()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.permission = 'admin' THEN
        PERFORM kma_assert_space_active_admin(OLD.space_id);
    END IF;
    RETURN OLD;
END $$;

CREATE FUNCTION kma_acl_org_status_guard()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (
            SELECT 1 FROM knowledge_space_acl a
            WHERE a.principal_type = 'org' AND a.principal_value = OLD.org_code
        ) THEN
            RAISE EXCEPTION USING ERRCODE = '23503', MESSAGE = 'ACL_PRINCIPAL_IN_USE';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'active' AND NEW.status <> 'active' THEN
        PERFORM kma_assert_principal_admin_spaces('org', OLD.org_code);
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION kma_acl_role_status_guard()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (
            SELECT 1 FROM knowledge_space_acl a
            WHERE a.principal_type = 'role' AND a.principal_value = OLD.role_code
        ) THEN
            RAISE EXCEPTION USING ERRCODE = '23503', MESSAGE = 'ACL_PRINCIPAL_IN_USE';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'active' AND NEW.status <> 'active' THEN
        PERFORM kma_assert_principal_admin_spaces('role', OLD.role_code);
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION kma_acl_user_status_guard()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (
            SELECT 1 FROM knowledge_space_acl a
            WHERE a.principal_type = 'user' AND a.principal_value = OLD.user_id::text
        ) THEN
            RAISE EXCEPTION USING ERRCODE = '23503', MESSAGE = 'ACL_PRINCIPAL_IN_USE';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'active' AND NEW.status <> 'active' THEN
        PERFORM kma_assert_principal_admin_spaces('user', OLD.user_id::text);
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_acl_validate_principal
BEFORE INSERT OR UPDATE OF principal_type, principal_value, permission
ON knowledge_space_acl
FOR EACH ROW EXECUTE FUNCTION kma_validate_acl_principal();

CREATE CONSTRAINT TRIGGER ctr_acl_last_admin
AFTER DELETE OR UPDATE ON knowledge_space_acl
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION kma_acl_admin_guard();

CREATE CONSTRAINT TRIGGER ctr_acl_org_status_guard
AFTER UPDATE ON kma_org
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION kma_acl_org_status_guard();

CREATE TRIGGER trg_acl_org_delete_guard
BEFORE DELETE ON kma_org
FOR EACH ROW EXECUTE FUNCTION kma_acl_org_status_guard();

CREATE CONSTRAINT TRIGGER ctr_acl_role_status_guard
AFTER UPDATE ON kma_role
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION kma_acl_role_status_guard();

CREATE TRIGGER trg_acl_role_delete_guard
BEFORE DELETE ON kma_role
FOR EACH ROW EXECUTE FUNCTION kma_acl_role_status_guard();

CREATE CONSTRAINT TRIGGER ctr_acl_user_status_guard
AFTER UPDATE ON kma_user
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION kma_acl_user_status_guard();

CREATE TRIGGER trg_acl_user_delete_guard
BEFORE DELETE ON kma_user
FOR EACH ROW EXECUTE FUNCTION kma_acl_user_status_guard();

-- PostgreSQL keeps historical constraint and index names when their tenant columns
-- are removed. Rename those catalog objects so the resulting schema is clean too.
DO $$
DECLARE
    item RECORD;
    clean_name TEXT;
BEGIN
    FOR item IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND (conname ILIKE '%tenant%' OR conname ILIKE '%quota%')
        ORDER BY conrelid::regclass::text, conname
    LOOP
        clean_name := regexp_replace(
            regexp_replace(item.conname, 'tenant', 'scope', 'gi'),
            'quota', 'usage', 'gi'
        );
        EXECUTE format(
            'ALTER TABLE %s RENAME CONSTRAINT %I TO %I',
            item.table_name, item.conname, clean_name
        );
    END LOOP;

    FOR item IN
        SELECT c.oid::regclass AS index_name, c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('i', 'I')
          AND (c.relname ILIKE '%tenant%' OR c.relname ILIKE '%quota%')
        ORDER BY c.relname
    LOOP
        clean_name := regexp_replace(
            regexp_replace(item.relname, 'tenant', 'scope', 'gi'),
            'quota', 'usage', 'gi'
        );
        EXECUTE format('ALTER INDEX %s RENAME TO %I', item.index_name, clean_name);
    END LOOP;
END $$;
