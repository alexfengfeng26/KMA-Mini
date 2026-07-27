-- KMA-FRONTEND.5：V3 响应式布局树与租户沙箱代码包。

ALTER TABLE knowledge_portal_config_version
    DROP CONSTRAINT ck_portal_config_schema,
    ADD CONSTRAINT ck_portal_config_schema CHECK (schema_version IN (2,3));

CREATE TABLE knowledge_portal_code_package (
    package_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    package_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'draft',
    current_version_id BIGINT,
    created_by BIGINT,
    updated_by BIGINT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_portal_code_package_key CHECK (package_key ~ '^[a-z][a-z0-9_-]{1,63}$'),
    CONSTRAINT ck_portal_code_package_status CHECK (status IN ('draft','active','revoked')),
    CONSTRAINT uk_portal_code_package_key UNIQUE (tenant_id,package_key),
    CONSTRAINT uk_portal_code_package_identity UNIQUE (tenant_id,package_id),
    CONSTRAINT fk_portal_code_package_creator FOREIGN KEY (tenant_id,created_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (created_by),
    CONSTRAINT fk_portal_code_package_updater FOREIGN KEY (tenant_id,updated_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (updated_by)
);

CREATE TABLE knowledge_portal_code_version (
    code_version_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    package_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    version_label VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'draft',
    source_mode VARCHAR(16) NOT NULL,
    entry_path VARCHAR(128) NOT NULL DEFAULT 'index.html',
    manifest_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    checksum VARCHAR(64) NOT NULL,
    scan_status VARCHAR(16) NOT NULL DEFAULT 'pending',
    scan_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    file_count INT NOT NULL DEFAULT 0,
    compressed_size BIGINT NOT NULL DEFAULT 0,
    expanded_size BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    published_by BIGINT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    scanned_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_portal_code_version_status CHECK (status IN ('draft','published','revoked')),
    CONSTRAINT ck_portal_code_source_mode CHECK (source_mode IN ('editor','zip')),
    CONSTRAINT ck_portal_code_scan_status CHECK (scan_status IN ('pending','passed','failed')),
    CONSTRAINT ck_portal_code_version_limits CHECK (
        version_no > 0 AND file_count BETWEEN 0 AND 50
        AND compressed_size BETWEEN 0 AND 2097152
        AND expanded_size BETWEEN 0 AND 10485760
    ),
    CONSTRAINT ck_portal_code_entry CHECK (entry_path ~ '^[A-Za-z0-9][A-Za-z0-9_./-]{0,127}$'),
    CONSTRAINT uk_portal_code_version UNIQUE (tenant_id,package_id,version_no),
    CONSTRAINT uk_portal_code_version_label UNIQUE (tenant_id,package_id,version_label),
    CONSTRAINT uk_portal_code_version_identity UNIQUE (tenant_id,code_version_id),
    CONSTRAINT fk_portal_code_version_package FOREIGN KEY (tenant_id,package_id)
        REFERENCES knowledge_portal_code_package(tenant_id,package_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_code_version_creator FOREIGN KEY (tenant_id,created_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (created_by),
    CONSTRAINT fk_portal_code_version_publisher FOREIGN KEY (tenant_id,published_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (published_by)
);

ALTER TABLE knowledge_portal_code_package
    ADD CONSTRAINT fk_portal_code_current_version
    FOREIGN KEY (tenant_id,current_version_id)
    REFERENCES knowledge_portal_code_version(tenant_id,code_version_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE knowledge_portal_code_file (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    code_version_id BIGINT NOT NULL,
    file_path VARCHAR(256) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id,code_version_id,file_path),
    CONSTRAINT ck_portal_code_file_path CHECK (
        file_path ~ '^[A-Za-z0-9][A-Za-z0-9_./-]{0,255}$'
        AND file_path !~ '(^|/)[.][.](/|$)'
    ),
    CONSTRAINT ck_portal_code_file_size CHECK (size_bytes BETWEEN 0 AND 10485760),
    CONSTRAINT fk_portal_code_file_version FOREIGN KEY (tenant_id,code_version_id)
        REFERENCES knowledge_portal_code_version(tenant_id,code_version_id) ON DELETE CASCADE
);

CREATE TABLE knowledge_portal_code_usage (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL,
    config_version_id BIGINT NOT NULL,
    page_slug VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    package_id BIGINT NOT NULL,
    code_version_id BIGINT NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id,site_id,config_version_id,page_slug,node_id),
    CONSTRAINT fk_portal_code_usage_site FOREIGN KEY (tenant_id,site_id)
        REFERENCES knowledge_portal_site(tenant_id,site_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_code_usage_config FOREIGN KEY (tenant_id,config_version_id)
        REFERENCES knowledge_portal_config_version(tenant_id,config_version_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_code_usage_package FOREIGN KEY (tenant_id,package_id)
        REFERENCES knowledge_portal_code_package(tenant_id,package_id) ON DELETE RESTRICT,
    CONSTRAINT fk_portal_code_usage_version FOREIGN KEY (tenant_id,code_version_id)
        REFERENCES knowledge_portal_code_version(tenant_id,code_version_id) ON DELETE RESTRICT
);

CREATE INDEX idx_portal_code_catalog
    ON knowledge_portal_code_package(tenant_id,status,package_key);
CREATE INDEX idx_portal_code_version
    ON knowledge_portal_code_version(tenant_id,package_id,status,version_no DESC);
CREATE INDEX idx_portal_code_usage_version
    ON knowledge_portal_code_usage(tenant_id,package_id,code_version_id);

INSERT INTO kma_permission
    (permission_code,name,parent_code,permission_type,permission_scope,description,sort_order,enabled,assignable)
VALUES
    ('portal-code:read','查看租户门户代码组件','portal-site:read','action','tenant','查看租户内沙箱代码组件与版本',1732,TRUE,TRUE),
    ('portal-code:edit','编辑租户门户代码组件','portal-code:read','action','tenant','在线编辑或导入静态组件资源',1733,TRUE,TRUE),
    ('portal-code:publish','发布租户门户代码组件','portal-code:read','action','tenant','发布通过安全扫描的不可变代码版本',1734,TRUE,TRUE),
    ('portal-code:revoke','撤销租户门户代码组件','portal-code:read','action','tenant','撤销未被门户发布版本使用的代码版本',1735,TRUE,TRUE)
ON CONFLICT (permission_code) DO UPDATE SET
    name=EXCLUDED.name,parent_code=EXCLUDED.parent_code,permission_type=EXCLUDED.permission_type,
    permission_scope=EXCLUDED.permission_scope,description=EXCLUDED.description,sort_order=EXCLUDED.sort_order,
    enabled=TRUE,assignable=TRUE;

INSERT INTO kma_role_permission(tenant_id,role_id,permission_code)
SELECT r.tenant_id,r.role_id,p.permission_code
FROM kma_role r
JOIN kma_permission p ON p.permission_code LIKE 'portal-code:%'
WHERE r.role_code IN ('kma-admin','tenant-admin')
ON CONFLICT DO NOTHING;

INSERT INTO kma_role_permission(tenant_id,role_id,permission_code)
SELECT r.tenant_id,r.role_id,p.permission_code
FROM kma_role r
JOIN kma_permission p ON p.permission_code IN ('portal-code:read','portal-code:edit')
WHERE r.role_code='knowledge-admin'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE knowledge_portal_code_package IS '租户自有低代码沙箱组件目录';
COMMENT ON TABLE knowledge_portal_code_version IS '通过扫描后不可变发布的租户沙箱组件版本';
COMMENT ON TABLE knowledge_portal_code_file IS '沙箱组件静态文件；禁止服务器端构建和执行';
