CREATE TABLE kma_tenant (
    tenant_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE kma_user (
    user_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    username VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    password_hash VARCHAR(512),
    identity_provider VARCHAR(32) NOT NULL DEFAULT 'local',
    external_subject VARCHAR(256),
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, username),
    UNIQUE (tenant_id, identity_provider, external_subject)
);

CREATE TABLE kma_role (
    role_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    role_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, role_code)
);

CREATE TABLE kma_permission (
    permission_code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL
);

CREATE TABLE kma_user_role (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    user_id BIGINT NOT NULL REFERENCES kma_user(user_id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES kma_role(role_id) ON DELETE CASCADE,
    PRIMARY KEY (tenant_id, user_id, role_id)
);

CREATE TABLE kma_role_permission (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    role_id BIGINT NOT NULL REFERENCES kma_role(role_id) ON DELETE CASCADE,
    permission_code VARCHAR(64) NOT NULL REFERENCES kma_permission(permission_code),
    PRIMARY KEY (tenant_id, role_id, permission_code)
);

CREATE TABLE kma_refresh_token (
    token_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id),
    user_id BIGINT NOT NULL REFERENCES kma_user(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    replaced_by UUID,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    last_used_time TIMESTAMP
);
CREATE INDEX idx_refresh_token_user ON kma_refresh_token(tenant_id, user_id, expires_at);

INSERT INTO kma_permission(permission_code, name) VALUES
    ('kma:admin', '平台管理'),
    ('tenant:manage', '租户管理'),
    ('user:manage', '用户管理'),
    ('space:manage', '知识空间管理'),
    ('document:ingest', '文档入库'),
    ('qa:use', '知识问答'),
    ('audit:read', '审计查看'),
    ('model:manage', '模型配置'),
    ('evaluation:manage', '评测管理')
ON CONFLICT DO NOTHING;

INSERT INTO kma_tenant(tenant_id, name)
VALUES ('default', '默认租户')
ON CONFLICT DO NOTHING;
