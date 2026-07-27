ALTER TABLE kma_permission
    ADD COLUMN parent_code VARCHAR(64),
    ADD COLUMN permission_type VARCHAR(16) NOT NULL DEFAULT 'legacy',
    ADD COLUMN permission_scope VARCHAR(16) NOT NULL DEFAULT 'tenant',
    ADD COLUMN description VARCHAR(512),
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN assignable BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE kma_role
    ADD COLUMN description VARCHAR(512),
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active';

ALTER TABLE kma_user
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE kma_user
    ADD CONSTRAINT uk_kma_user_tenant_id UNIQUE (tenant_id, user_id);

CREATE TABLE kma_org (
    org_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    org_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, org_code),
    UNIQUE (tenant_id, org_id),
    CHECK (status IN ('active', 'disabled'))
);

ALTER TABLE kma_org
    ADD CONSTRAINT fk_kma_org_parent_tenant
        FOREIGN KEY (tenant_id, parent_id) REFERENCES kma_org(tenant_id, org_id);

CREATE INDEX idx_kma_org_parent ON kma_org(tenant_id, parent_id, sort_order, org_id);

CREATE TABLE kma_org_closure (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth INT NOT NULL,
    PRIMARY KEY (tenant_id, ancestor_id, descendant_id),
    FOREIGN KEY (tenant_id, ancestor_id) REFERENCES kma_org(tenant_id, org_id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, descendant_id) REFERENCES kma_org(tenant_id, org_id) ON DELETE CASCADE,
    CHECK (depth >= 0)
);
CREATE INDEX idx_kma_org_closure_descendant
    ON kma_org_closure(tenant_id, descendant_id, depth, ancestor_id);

CREATE TABLE kma_user_org (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    primary_org BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, org_id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES kma_user(tenant_id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, org_id) REFERENCES kma_org(tenant_id, org_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uk_kma_user_primary_org
    ON kma_user_org(tenant_id, user_id) WHERE primary_org;
CREATE INDEX idx_kma_user_org_org ON kma_user_org(tenant_id, org_id, user_id);

INSERT INTO kma_permission
    (permission_code, name, parent_code, permission_type, permission_scope, description, sort_order, enabled, assignable)
VALUES
    ('dashboard:read', '查看运行概览', NULL, 'menu', 'tenant', '查看租户运行指标和依赖状态', 100, TRUE, TRUE),
    ('tenant:read', '查看租户', NULL, 'menu', 'platform', '查看全部租户', 200, TRUE, TRUE),
    ('tenant:create', '创建租户', 'tenant:read', 'action', 'platform', '创建租户及初始化管理员', 201, TRUE, TRUE),
    ('tenant:update', '更新租户', 'tenant:read', 'action', 'platform', '更新或停用租户', 202, TRUE, TRUE),
    ('space:read', '查看知识空间', NULL, 'menu', 'tenant', '查看被授权的知识空间', 300, TRUE, TRUE),
    ('space:create', '创建知识空间', 'space:read', 'action', 'tenant', '创建知识空间', 301, TRUE, TRUE),
    ('space:update', '编辑知识空间', 'space:read', 'action', 'tenant', '编辑空间配置和状态', 302, TRUE, TRUE),
    ('space:delete', '删除知识空间', 'space:read', 'action', 'tenant', '删除空间及关联数据', 303, TRUE, TRUE),
    ('space:acl:manage', '管理空间 ACL', 'space:read', 'action', 'tenant', '维护用户、角色和组织空间授权', 304, TRUE, TRUE),
    ('space:reindex', '重建空间索引', 'space:read', 'action', 'tenant', '重建空间内全部文档索引', 305, TRUE, TRUE),
    ('dataset:read', '查看数据集', NULL, 'menu', 'tenant', '查看数据集和向量版本', 400, TRUE, TRUE),
    ('dataset:create', '创建数据集', 'dataset:read', 'action', 'tenant', '创建数据集', 401, TRUE, TRUE),
    ('dataset:update', '编辑数据集', 'dataset:read', 'action', 'tenant', '编辑数据集配置', 402, TRUE, TRUE),
    ('dataset:delete', '删除数据集', 'dataset:read', 'action', 'tenant', '删除未绑定的数据集', 403, TRUE, TRUE),
    ('dataset:status:update', '更新数据集状态', 'dataset:read', 'action', 'tenant', '启用或停用数据集', 404, TRUE, TRUE),
    ('embedding:rebuild', '重建向量', 'dataset:read', 'action', 'tenant', '创建 Embedding 重建任务', 405, TRUE, TRUE),
    ('embedding:activate', '激活向量版本', 'dataset:read', 'action', 'tenant', '原子切换重建后的向量版本', 406, TRUE, TRUE),
    ('document:read', '查看文档', NULL, 'menu', 'tenant', '查看有权空间中的文档和版本', 500, TRUE, TRUE),
    ('document:ingest', '文档入库', 'document:read', 'action', 'tenant', '上传文件或文本入库', 501, TRUE, TRUE),
    ('document:reindex', '重建文档索引', 'document:read', 'action', 'tenant', '重新解析并索引文档', 502, TRUE, TRUE),
    ('document:delete', '删除文档', 'document:read', 'action', 'tenant', '删除文档和关联内容', 503, TRUE, TRUE),
    ('task:read', '查看入库任务', NULL, 'menu', 'tenant', '查看有权空间的任务和积压', 600, TRUE, TRUE),
    ('task:retry', '重试入库任务', 'task:read', 'action', 'tenant', '重试失败或死信任务', 601, TRUE, TRUE),
    ('storage:read', '查看存储生命周期', NULL, 'menu', 'tenant', '查看对象存储台账', 700, TRUE, TRUE),
    ('storage:reconcile', '执行存储对账', 'storage:read', 'action', 'tenant', '执行对象与引用对账', 701, TRUE, TRUE),
    ('storage:cleanup', '执行存储清理', 'storage:read', 'action', 'tenant', '清理到期孤儿对象', 702, TRUE, TRUE),
    ('retrieval:use', '使用检索调试', NULL, 'menu', 'tenant', '执行检索和分数调试', 800, TRUE, TRUE),
    ('qa:use', '使用知识问答', NULL, 'menu', 'tenant', '使用普通和流式知识问答', 900, TRUE, TRUE),
    ('chat:read', '查看问答会话', 'qa:use', 'action', 'tenant', '查看个人问答会话和消息', 901, TRUE, TRUE),
    ('model:read', '查看模型配置', NULL, 'menu', 'tenant', '查看模型 Profile', 1000, TRUE, TRUE),
    ('model:create', '创建模型配置', 'model:read', 'action', 'tenant', '创建模型 Profile', 1001, TRUE, TRUE),
    ('model:update', '编辑模型配置', 'model:read', 'action', 'tenant', '编辑和启停模型 Profile', 1002, TRUE, TRUE),
    ('evaluation:read', '查看 RAG 评测', NULL, 'menu', 'tenant', '查看评测集、运行和门禁', 1100, TRUE, TRUE),
    ('evaluation:create', '创建评测集', 'evaluation:read', 'action', 'tenant', '创建 RAG 评测集', 1101, TRUE, TRUE),
    ('evaluation:case:create', '添加评测用例', 'evaluation:read', 'action', 'tenant', '向评测集添加标准问答用例', 1102, TRUE, TRUE),
    ('evaluation:run', '运行 RAG 评测', 'evaluation:read', 'action', 'tenant', '运行评测任务', 1103, TRUE, TRUE),
    ('evaluation:gate:update', '配置评测门禁', 'evaluation:read', 'action', 'tenant', '更新发布门禁阈值', 1104, TRUE, TRUE),
    ('evaluation:release:assert', '验证评测发布', 'evaluation:read', 'action', 'tenant', '验证评测结果是否允许发布', 1105, TRUE, TRUE),
    ('audit:call:read', '查看调用审计', NULL, 'menu', 'tenant', '查看问答调用日志及详情', 1200, TRUE, TRUE),
    ('audit:security:read', '查看安全审计', NULL, 'menu', 'tenant', '查看身份、权限和内容安全审计', 1201, TRUE, TRUE),
    ('user:read', '查看用户', NULL, 'menu', 'tenant', '查看租户用户', 1300, TRUE, TRUE),
    ('user:create', '创建用户', 'user:read', 'action', 'tenant', '创建本地用户', 1301, TRUE, TRUE),
    ('user:status:update', '更新用户状态', 'user:read', 'action', 'tenant', '启用或停用用户', 1302, TRUE, TRUE),
    ('user:role:assign', '分配用户角色', 'user:read', 'action', 'tenant', '维护用户角色', 1303, TRUE, TRUE),
    ('user:password:reset', '重置用户密码', 'user:read', 'action', 'tenant', '重置本地用户密码', 1304, TRUE, TRUE),
    ('user:token:revoke', '撤销用户令牌', 'user:read', 'action', 'tenant', '撤销用户全部登录令牌', 1305, TRUE, TRUE),
    ('role:read', '查看角色权限', NULL, 'menu', 'tenant', '查看角色和授权', 1400, TRUE, TRUE),
    ('role:create', '创建角色', 'role:read', 'action', 'tenant', '创建租户角色', 1401, TRUE, TRUE),
    ('role:update', '编辑角色', 'role:read', 'action', 'tenant', '编辑角色、状态和权限', 1402, TRUE, TRUE),
    ('role:delete', '删除角色', 'role:read', 'action', 'tenant', '删除未使用的自定义角色', 1403, TRUE, TRUE),
    ('permission:read', '查看权限目录', 'role:read', 'action', 'tenant', '查看可分配权限树', 1404, TRUE, TRUE),
    ('org:read', '查看组织', NULL, 'menu', 'tenant', '查看租户组织树和成员', 1500, TRUE, TRUE),
    ('org:create', '创建组织', 'org:read', 'action', 'tenant', '创建下级组织', 1501, TRUE, TRUE),
    ('org:update', '编辑组织', 'org:read', 'action', 'tenant', '编辑组织信息和状态', 1502, TRUE, TRUE),
    ('org:move', '移动组织', 'org:read', 'action', 'tenant', '调整组织父节点', 1503, TRUE, TRUE),
    ('org:delete', '删除组织', 'org:read', 'action', 'tenant', '删除空的非内置组织', 1504, TRUE, TRUE),
    ('org:member:manage', '管理组织成员', 'org:read', 'action', 'tenant', '维护用户组织归属', 1505, TRUE, TRUE),
    ('quota:read', '查看租户配额', NULL, 'menu', 'tenant', '查看租户配额和使用量', 1600, TRUE, TRUE),
    ('quota:update', '更新租户配额', 'quota:read', 'action', 'tenant', '更新租户配额', 1601, TRUE, TRUE)
ON CONFLICT (permission_code) DO UPDATE SET
    name = EXCLUDED.name,
    parent_code = EXCLUDED.parent_code,
    permission_type = EXCLUDED.permission_type,
    permission_scope = EXCLUDED.permission_scope,
    description = EXCLUDED.description,
    sort_order = EXCLUDED.sort_order,
    enabled = EXCLUDED.enabled,
    assignable = EXCLUDED.assignable;

UPDATE kma_permission SET assignable=FALSE, enabled=TRUE
WHERE permission_code IN ('kma:admin','tenant:manage','user:manage','space:manage','audit:read',
                          'model:manage','evaluation:manage','quota:manage','security:audit','storage:manage');

ALTER TABLE kma_permission
    ADD CONSTRAINT fk_kma_permission_parent
        FOREIGN KEY (parent_code) REFERENCES kma_permission(permission_code),
    ADD CONSTRAINT ck_kma_permission_type
        CHECK (permission_type IN ('menu', 'action', 'legacy')),
    ADD CONSTRAINT ck_kma_permission_scope
        CHECK (permission_scope IN ('platform', 'tenant'));

ALTER TABLE kma_role
    ADD CONSTRAINT ck_kma_role_status CHECK (status IN ('active', 'disabled'));

INSERT INTO kma_org(tenant_id, org_code, name, parent_id, status, built_in, sort_order)
SELECT tenant_id, 'root', name || '根组织', NULL, 'active', TRUE, 0 FROM kma_tenant
ON CONFLICT (tenant_id, org_code) DO NOTHING;

INSERT INTO kma_org_closure(tenant_id, ancestor_id, descendant_id, depth)
SELECT tenant_id, org_id, org_id, 0 FROM kma_org
ON CONFLICT DO NOTHING;

INSERT INTO kma_role(tenant_id, role_code, name, description, built_in, status)
SELECT tenant_id, role_code, role_name, description, TRUE, 'active'
FROM kma_tenant
CROSS JOIN (VALUES
    ('tenant-admin', '租户管理员', '租户内全部管理权限'),
    ('knowledge-admin', '知识管理员', '知识空间、文档、模型和评测管理'),
    ('knowledge-editor', '知识编辑者', '文档入库、任务处理、检索和问答'),
    ('knowledge-reader', '知识只读用户', '知识检索、问答和会话查看'),
    ('auditor', '审计员', '运行概览、调用日志和安全审计')
) AS templates(role_code, role_name, description)
ON CONFLICT (tenant_id, role_code) DO UPDATE SET
    name=EXCLUDED.name, description=EXCLUDED.description, built_in=TRUE, update_time=now();

INSERT INTO kma_role_permission(tenant_id, role_id, permission_code)
SELECT r.tenant_id, r.role_id, p.permission_code
FROM kma_role r JOIN kma_permission p ON p.enabled
WHERE r.role_code='kma-admin'
ON CONFLICT DO NOTHING;

INSERT INTO kma_role_permission(tenant_id, role_id, permission_code)
SELECT r.tenant_id, r.role_id, p.permission_code
FROM kma_role r JOIN kma_permission p ON p.enabled AND p.permission_scope='tenant' AND p.permission_type<>'legacy'
WHERE r.role_code='tenant-admin'
ON CONFLICT DO NOTHING;

WITH role_targets(role_code, permission_code) AS (VALUES
    ('knowledge-admin','space:read'),('knowledge-admin','space:create'),('knowledge-admin','space:update'),
    ('knowledge-admin','space:delete'),('knowledge-admin','space:acl:manage'),('knowledge-admin','space:reindex'),
    ('knowledge-admin','dataset:read'),('knowledge-admin','dataset:create'),('knowledge-admin','dataset:update'),
    ('knowledge-admin','dataset:delete'),('knowledge-admin','dataset:status:update'),
    ('knowledge-admin','embedding:rebuild'),('knowledge-admin','embedding:activate'),
    ('knowledge-admin','document:read'),('knowledge-admin','document:ingest'),('knowledge-admin','document:reindex'),
    ('knowledge-admin','document:delete'),('knowledge-admin','task:read'),('knowledge-admin','task:retry'),
    ('knowledge-admin','retrieval:use'),('knowledge-admin','qa:use'),('knowledge-admin','chat:read'),
    ('knowledge-admin','model:read'),('knowledge-admin','model:create'),('knowledge-admin','model:update'),
    ('knowledge-admin','evaluation:read'),('knowledge-admin','evaluation:create'),
    ('knowledge-admin','evaluation:case:create'),('knowledge-admin','evaluation:run'),
    ('knowledge-admin','evaluation:gate:update'),('knowledge-admin','evaluation:release:assert'),
    ('knowledge-editor','space:read'),('knowledge-editor','document:read'),
    ('knowledge-editor','document:ingest'),('knowledge-editor','document:reindex'),
    ('knowledge-editor','task:read'),('knowledge-editor','task:retry'),
    ('knowledge-editor','retrieval:use'),('knowledge-editor','qa:use'),('knowledge-editor','chat:read'),
    ('knowledge-reader','space:read'),('knowledge-reader','document:read'),
    ('knowledge-reader','retrieval:use'),('knowledge-reader','qa:use'),('knowledge-reader','chat:read'),
    ('auditor','dashboard:read'),('auditor','audit:call:read'),('auditor','audit:security:read')
)
INSERT INTO kma_role_permission(tenant_id, role_id, permission_code)
SELECT r.tenant_id, r.role_id, t.permission_code
FROM role_targets t JOIN kma_role r ON r.role_code=t.role_code
ON CONFLICT DO NOTHING;

WITH permission_mapping(source_code, target_code) AS (VALUES
    ('tenant:manage','tenant:read'),('tenant:manage','tenant:create'),('tenant:manage','tenant:update'),
    ('user:manage','user:read'),('user:manage','user:create'),('user:manage','user:status:update'),
    ('user:manage','user:role:assign'),('user:manage','user:password:reset'),('user:manage','user:token:revoke'),
    ('user:manage','role:read'),('user:manage','role:create'),('user:manage','role:update'),
    ('user:manage','role:delete'),('user:manage','permission:read'),
    ('user:manage','org:read'),('user:manage','org:create'),('user:manage','org:update'),
    ('user:manage','org:move'),('user:manage','org:delete'),('user:manage','org:member:manage'),
    ('space:manage','space:read'),('space:manage','space:create'),('space:manage','space:update'),
    ('space:manage','space:delete'),('space:manage','space:acl:manage'),('space:manage','space:reindex'),
    ('space:manage','dataset:read'),('space:manage','dataset:create'),('space:manage','dataset:update'),
    ('space:manage','dataset:delete'),('space:manage','dataset:status:update'),
    ('space:manage','embedding:rebuild'),('space:manage','embedding:activate'),
    ('document:ingest','document:read'),('document:ingest','document:reindex'),('document:ingest','document:delete'),
    ('document:ingest','task:read'),('document:ingest','task:retry'),
    ('qa:use','retrieval:use'),('qa:use','chat:read'),
    ('audit:read','dashboard:read'),('audit:read','audit:call:read'),('audit:read','audit:security:read'),
    ('security:audit','audit:security:read'),
    ('model:manage','model:read'),('model:manage','model:create'),('model:manage','model:update'),
    ('evaluation:manage','evaluation:read'),('evaluation:manage','evaluation:create'),
    ('evaluation:manage','evaluation:case:create'),('evaluation:manage','evaluation:run'),
    ('evaluation:manage','evaluation:gate:update'),('evaluation:manage','evaluation:release:assert'),
    ('quota:manage','quota:read'),('quota:manage','quota:update'),
    ('storage:manage','storage:read'),('storage:manage','storage:reconcile'),('storage:manage','storage:cleanup')
)
INSERT INTO kma_role_permission(tenant_id, role_id, permission_code)
SELECT rp.tenant_id, rp.role_id, m.target_code
FROM kma_role_permission rp JOIN permission_mapping m ON m.source_code=rp.permission_code
ON CONFLICT DO NOTHING;

UPDATE knowledge_space_acl acl
SET principal_value=r.role_code
FROM kma_role r
WHERE acl.principal_type='role'
  AND acl.tenant_id=r.tenant_id
  AND acl.principal_value ~ '^[0-9]+$'
  AND r.role_id=acl.principal_value::BIGINT;

-- V2 的默认空间在身份表建立前使用了数字占位角色；无法解析的历史数字授权安全归并到租户管理员。
UPDATE knowledge_space_acl
SET principal_value='tenant-admin'
WHERE principal_type='role' AND principal_value ~ '^[0-9]+$';

INSERT INTO knowledge_space_acl(tenant_id, space_id, principal_type, principal_value, permission)
SELECT s.tenant_id, s.space_id, 'role', 'tenant-admin', 'admin'
FROM knowledge_space s
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_space_acl acl
    WHERE acl.tenant_id=s.tenant_id AND acl.space_id=s.space_id AND acl.permission='admin'
)
ON CONFLICT DO NOTHING;

ALTER TABLE knowledge_space_acl
    ADD CONSTRAINT ck_space_acl_principal_type CHECK (principal_type IN ('user','role','org')),
    ADD CONSTRAINT ck_space_acl_permission CHECK (permission IN ('read','ingest','admin'));

CREATE INDEX idx_kma_permission_tree ON kma_permission(permission_type, sort_order, permission_code);
CREATE INDEX idx_kma_role_status ON kma_role(tenant_id, status, role_code);
