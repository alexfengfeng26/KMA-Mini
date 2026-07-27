-- KMA 党建知识库：内容治理、专题、门户收藏与阅读历史。
-- 旧技术文档默认 publication_managed=false，保持解析成功即激活的兼容行为。

ALTER TABLE knowledge_doc
    ADD COLUMN publication_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN content_type VARCHAR(32),
    ADD COLUMN document_number VARCHAR(128),
    ADD COLUMN issuing_authority VARCHAR(255),
    ADD COLUMN publish_date DATE,
    ADD COLUMN effective_date DATE,
    ADD COLUMN expiry_date DATE,
    ADD COLUMN validity_status VARCHAR(24) NOT NULL DEFAULT 'effective',
    ADD COLUMN workflow_status VARCHAR(24) NOT NULL DEFAULT 'published',
    ADD COLUMN review_decision VARCHAR(24),
    ADD COLUMN review_note VARCHAR(1000),
    ADD COLUMN online BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN summary TEXT,
    ADD COLUMN keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN reviewer_id BIGINT,
    ADD COLUMN submitted_at TIMESTAMP,
    ADD COLUMN reviewed_at TIMESTAMP,
    ADD COLUMN published_at TIMESTAMP;

ALTER TABLE knowledge_doc
    ADD CONSTRAINT ck_doc_content_type CHECK (content_type IS NULL OR content_type IN
        ('party_constitution','policy','learning_material','grassroots_case','organization_system')),
    ADD CONSTRAINT ck_doc_validity_status CHECK (validity_status IN
        ('effective','pending','expired','repealed','unknown')),
    ADD CONSTRAINT ck_doc_workflow_status CHECK (workflow_status IN ('draft','reviewing','published')),
    ADD CONSTRAINT ck_doc_review_decision CHECK (review_decision IS NULL OR review_decision IN
        ('pending','approved','rejected')),
    ADD CONSTRAINT ck_doc_validity_dates CHECK
        (expiry_date IS NULL OR effective_date IS NULL OR expiry_date >= effective_date),
    ADD CONSTRAINT fk_doc_reviewer_tenant FOREIGN KEY (tenant_id,reviewer_id)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (reviewer_id);

CREATE INDEX idx_doc_portal_visibility ON knowledge_doc
    (tenant_id,space_id,content_type,validity_status,publish_date DESC)
    WHERE publication_managed AND workflow_status='published' AND online AND is_active;
CREATE INDEX idx_doc_governance_workflow ON knowledge_doc
    (tenant_id,workflow_status,review_decision,update_time DESC) WHERE publication_managed;
CREATE INDEX idx_doc_number_authority ON knowledge_doc(tenant_id,document_number,issuing_authority)
    WHERE publication_managed;

CREATE TABLE knowledge_topic (
    topic_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    topic_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1000),
    cover_color VARCHAR(24),
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    system_topic BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id,topic_code),
    UNIQUE (tenant_id,topic_id)
);
CREATE INDEX idx_topic_tenant_order ON knowledge_topic(tenant_id,enabled,sort_order,topic_id);

CREATE TABLE knowledge_doc_topic (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    doc_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id,doc_id,topic_id),
    FOREIGN KEY (tenant_id,doc_id) REFERENCES knowledge_doc(tenant_id,doc_id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id,topic_id) REFERENCES knowledge_topic(tenant_id,topic_id) ON DELETE CASCADE
);
CREATE INDEX idx_doc_topic_topic ON knowledge_doc_topic(tenant_id,topic_id,doc_id);

CREATE TABLE knowledge_favorite (
    favorite_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    favorite_type VARCHAR(16) NOT NULL,
    doc_id BIGINT,
    session_id BIGINT,
    title VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id,user_id) REFERENCES kma_user(tenant_id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id,doc_id) REFERENCES knowledge_doc(tenant_id,doc_id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id,session_id) REFERENCES knowledge_chat_session(tenant_id,session_id) ON DELETE CASCADE,
    CHECK (favorite_type IN ('content','qa')),
    CHECK ((favorite_type='content' AND doc_id IS NOT NULL AND session_id IS NULL)
        OR (favorite_type='qa' AND session_id IS NOT NULL AND doc_id IS NULL))
);
CREATE UNIQUE INDEX uk_favorite_content ON knowledge_favorite(tenant_id,user_id,doc_id)
    WHERE favorite_type='content';
CREATE UNIQUE INDEX uk_favorite_qa ON knowledge_favorite(tenant_id,user_id,session_id)
    WHERE favorite_type='qa';
CREATE INDEX idx_favorite_user_time ON knowledge_favorite(tenant_id,user_id,create_time DESC);

CREATE TABLE knowledge_read_history (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    last_location VARCHAR(128),
    last_read_at TIMESTAMP NOT NULL DEFAULT now(),
    read_count INT NOT NULL DEFAULT 1,
    PRIMARY KEY (tenant_id,user_id,doc_id),
    FOREIGN KEY (tenant_id,user_id) REFERENCES kma_user(tenant_id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id,doc_id) REFERENCES knowledge_doc(tenant_id,doc_id) ON DELETE CASCADE
);
CREATE INDEX idx_read_history_user_time ON knowledge_read_history(tenant_id,user_id,last_read_at DESC);

CREATE TABLE knowledge_portal_config (
    tenant_id VARCHAR(64) PRIMARY KEY REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    unit_name VARCHAR(255) NOT NULL,
    help_text VARCHAR(1000),
    current_topic_code VARCHAR(64),
    update_time TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO knowledge_topic(tenant_id,topic_code,name,description,sort_order,featured,system_topic)
SELECT t.tenant_id, v.code, v.name, v.description, v.sort_order, TRUE, TRUE
FROM kma_tenant t CROSS JOIN (VALUES
    ('party_constitution','党章党规','党章、准则、条例和党内法规',10),
    ('policy','政策文件','中央及各级党组织权威政策文件',20),
    ('learning_material','学习材料','理论学习、专题辅导和学习参考',30),
    ('grassroots_case','基层案例','基层党建实践案例和经验做法',40),
    ('organization_system','组织工作制度','组织生活、党员管理和基层组织制度',50)
) AS v(code,name,description,sort_order)
ON CONFLICT (tenant_id,topic_code) DO NOTHING;

INSERT INTO knowledge_portal_config(tenant_id,unit_name,help_text)
SELECT tenant_id,name,'所有回答均应以已发布、有效且有权访问的材料为依据。' FROM kma_tenant
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO kma_permission
    (permission_code,name,parent_code,permission_type,permission_scope,description,sort_order,enabled,assignable)
VALUES
    ('content:read','查看党建内容',NULL,'menu','tenant','访问党建知识门户和治理内容列表',1700,TRUE,TRUE),
    ('content:create','创建党建内容','content:read','action','tenant','上传或创建党建内容草稿',1701,TRUE,TRUE),
    ('content:update','编辑党建内容','content:read','action','tenant','编辑草稿和内容元数据',1702,TRUE,TRUE),
    ('content:submit','提交内容审核','content:read','action','tenant','将草稿提交审核',1703,TRUE,TRUE),
    ('content:review','审核党建内容','content:read','action','tenant','审核通过或驳回内容',1704,TRUE,TRUE),
    ('content:publish','发布党建内容','content:read','action','tenant','发布、下线和恢复内容版本',1705,TRUE,TRUE),
    ('topic:manage','管理分类专题','content:read','action','tenant','维护专题、推荐状态和顺序',1706,TRUE,TRUE),
    ('portal:configure','配置知识门户','content:read','action','tenant','配置单位名称、当前专题和帮助文案',1707,TRUE,TRUE)
ON CONFLICT (permission_code) DO UPDATE SET
    name=EXCLUDED.name,parent_code=EXCLUDED.parent_code,permission_type=EXCLUDED.permission_type,
    permission_scope=EXCLUDED.permission_scope,description=EXCLUDED.description,sort_order=EXCLUDED.sort_order,
    enabled=TRUE,assignable=TRUE;

INSERT INTO kma_role_permission(tenant_id,role_id,permission_code)
SELECT r.tenant_id,r.role_id,p.permission_code
FROM kma_role r JOIN kma_permission p ON p.permission_code LIKE 'content:%'
    OR p.permission_code IN ('topic:manage','portal:configure')
WHERE r.role_code IN ('kma-admin','tenant-admin')
ON CONFLICT DO NOTHING;

WITH role_targets(role_code,permission_code) AS (VALUES
    ('knowledge-admin','content:read'),('knowledge-admin','content:create'),
    ('knowledge-admin','content:update'),('knowledge-admin','content:submit'),
    ('knowledge-admin','content:review'),('knowledge-admin','content:publish'),
    ('knowledge-admin','topic:manage'),('knowledge-admin','portal:configure'),
    ('knowledge-editor','content:read'),('knowledge-editor','content:create'),
    ('knowledge-editor','content:update'),('knowledge-editor','content:submit'),
    ('knowledge-reader','content:read')
)
INSERT INTO kma_role_permission(tenant_id,role_id,permission_code)
SELECT r.tenant_id,r.role_id,t.permission_code
FROM role_targets t JOIN kma_role r ON r.role_code=t.role_code
ON CONFLICT DO NOTHING;

COMMENT ON COLUMN knowledge_doc.publication_managed IS 'true 表示受草稿/审核/发布流程管理，发布前不得检索';
COMMENT ON COLUMN knowledge_doc.review_decision IS '审核决定独立于三态工作流，approved 后等待显式发布';
