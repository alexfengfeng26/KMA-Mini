-- KMA-FRONTEND.3: 场景化多站点 CMS、版本发布、资产与内容范围。

CREATE TABLE knowledge_portal_site (
    site_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    site_key VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    scenario VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    default_site BOOLEAN NOT NULL DEFAULT FALSE,
    current_published_version_id BIGINT,
    created_by BIGINT,
    updated_by BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_portal_site_key CHECK (site_key ~ '^[a-z][a-z0-9_-]{1,63}$'),
    CONSTRAINT ck_portal_site_scenario CHECK (scenario IN ('party','internal-policy','product-help')),
    CONSTRAINT ck_portal_site_status CHECK (status IN ('active','disabled')),
    CONSTRAINT uk_portal_site_key UNIQUE (tenant_id,site_key),
    CONSTRAINT uk_portal_site_identity UNIQUE (tenant_id,site_id),
    CONSTRAINT fk_portal_site_creator FOREIGN KEY (tenant_id,created_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (created_by),
    CONSTRAINT fk_portal_site_updater FOREIGN KEY (tenant_id,updated_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (updated_by)
);
CREATE UNIQUE INDEX uk_portal_site_default
    ON knowledge_portal_site(tenant_id) WHERE default_site;
CREATE INDEX idx_portal_site_tenant_status
    ON knowledge_portal_site(tenant_id,status,default_site DESC,site_id);

CREATE TABLE knowledge_portal_config_version (
    config_version_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    schema_version INT NOT NULL DEFAULT 2,
    config_json JSONB NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    lock_version INT NOT NULL DEFAULT 0,
    change_note VARCHAR(1000),
    created_by BIGINT,
    reviewed_by BIGINT,
    published_by BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    submitted_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    published_at TIMESTAMP,
    CONSTRAINT ck_portal_config_status CHECK (status IN ('draft','reviewing','published','archived')),
    CONSTRAINT ck_portal_config_schema CHECK (schema_version = 2),
    CONSTRAINT ck_portal_config_version CHECK (version_no > 0),
    CONSTRAINT uk_portal_config_version UNIQUE (tenant_id,site_id,version_no),
    CONSTRAINT uk_portal_config_identity UNIQUE (tenant_id,config_version_id),
    CONSTRAINT fk_portal_config_site FOREIGN KEY (tenant_id,site_id)
        REFERENCES knowledge_portal_site(tenant_id,site_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_config_creator FOREIGN KEY (tenant_id,created_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (created_by),
    CONSTRAINT fk_portal_config_reviewer FOREIGN KEY (tenant_id,reviewed_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (reviewed_by),
    CONSTRAINT fk_portal_config_publisher FOREIGN KEY (tenant_id,published_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (published_by)
);
CREATE INDEX idx_portal_config_site_status
    ON knowledge_portal_config_version(tenant_id,site_id,status,version_no DESC);

ALTER TABLE knowledge_portal_site
    ADD CONSTRAINT fk_portal_site_published_version
    FOREIGN KEY (tenant_id,current_published_version_id)
    REFERENCES knowledge_portal_config_version(tenant_id,config_version_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE knowledge_portal_asset (
    asset_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL,
    asset_key VARCHAR(96) NOT NULL,
    asset_type VARCHAR(24) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_portal_asset_key CHECK (asset_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,95}$'),
    CONSTRAINT ck_portal_asset_type CHECK (asset_type IN ('logo','favicon','background','icon','illustration')),
    CONSTRAINT ck_portal_asset_status CHECK (status IN ('active','orphaned')),
    CONSTRAINT ck_portal_asset_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT uk_portal_asset_key UNIQUE (tenant_id,site_id,asset_key),
    CONSTRAINT uk_portal_asset_identity UNIQUE (tenant_id,asset_id),
    CONSTRAINT fk_portal_asset_site FOREIGN KEY (tenant_id,site_id)
        REFERENCES knowledge_portal_site(tenant_id,site_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_asset_creator FOREIGN KEY (tenant_id,created_by)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (created_by)
);
CREATE INDEX idx_portal_asset_site ON knowledge_portal_asset(tenant_id,site_id,status,create_time DESC);

CREATE TABLE knowledge_portal_site_scope (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL,
    config_version_id BIGINT NOT NULL,
    scope_type VARCHAR(24) NOT NULL,
    scope_value VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id,site_id,config_version_id,scope_type,scope_value),
    CONSTRAINT ck_portal_scope_type CHECK (scope_type IN ('all','space','topic','content_type','validity')),
    CONSTRAINT fk_portal_scope_site FOREIGN KEY (tenant_id,site_id)
        REFERENCES knowledge_portal_site(tenant_id,site_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_scope_version FOREIGN KEY (tenant_id,config_version_id)
        REFERENCES knowledge_portal_config_version(tenant_id,config_version_id) ON DELETE CASCADE
);
CREATE INDEX idx_portal_scope_lookup
    ON knowledge_portal_site_scope(tenant_id,site_id,config_version_id,scope_type,scope_value);

CREATE TABLE knowledge_portal_event (
    event_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL,
    user_id BIGINT,
    event_type VARCHAR(32) NOT NULL,
    page_slug VARCHAR(64),
    query_text VARCHAR(500),
    target_id VARCHAR(128),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_portal_event_type CHECK (event_type IN
        ('page_view','search','search_empty','article_click','ai_ask','feedback')),
    CONSTRAINT fk_portal_event_site FOREIGN KEY (tenant_id,site_id)
        REFERENCES knowledge_portal_site(tenant_id,site_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_event_user FOREIGN KEY (tenant_id,user_id)
        REFERENCES kma_user(tenant_id,user_id) ON DELETE SET NULL (user_id)
);
CREATE INDEX idx_portal_event_analysis
    ON knowledge_portal_event(tenant_id,site_id,event_type,create_time DESC);

ALTER TABLE knowledge_topic
    ADD COLUMN parent_topic_id BIGINT,
    ADD COLUMN topic_type VARCHAR(24) NOT NULL DEFAULT 'topic',
    ADD COLUMN icon VARCHAR(64),
    ADD COLUMN slug VARCHAR(96),
    ADD COLUMN display_mode VARCHAR(24) NOT NULL DEFAULT 'list';

UPDATE knowledge_topic SET slug=topic_code WHERE slug IS NULL;

ALTER TABLE knowledge_topic
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT ck_topic_type CHECK (topic_type IN ('category','topic','channel')),
    ADD CONSTRAINT ck_topic_display_mode CHECK (display_mode IN ('list','cards','timeline','faq')),
    ADD CONSTRAINT ck_topic_slug CHECK (slug ~ '^[A-Za-z0-9][A-Za-z0-9_-]{0,95}$'),
    ADD CONSTRAINT fk_topic_parent FOREIGN KEY (tenant_id,parent_topic_id)
        REFERENCES knowledge_topic(tenant_id,topic_id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uk_topic_tenant_slug ON knowledge_topic(tenant_id,slug);
CREATE INDEX idx_topic_tree ON knowledge_topic(tenant_id,parent_topic_id,sort_order,topic_id);

INSERT INTO knowledge_portal_site
    (tenant_id,site_key,name,scenario,status,default_site,created_by,updated_by)
SELECT t.tenant_id,'default',COALESCE(pc.unit_name,t.name),'party','active',TRUE,NULL,NULL
FROM kma_tenant t
LEFT JOIN knowledge_portal_config pc ON pc.tenant_id=t.tenant_id
ON CONFLICT (tenant_id,site_key) DO NOTHING;

WITH defaults AS (
    SELECT s.tenant_id,s.site_id,s.site_key,s.name,
           COALESCE(pc.help_text,'所有回答均应以已发布、有效且有权访问的材料为依据。') help_text
    FROM knowledge_portal_site s
    LEFT JOIN knowledge_portal_config pc ON pc.tenant_id=s.tenant_id
    WHERE s.site_key='default'
), inserted AS (
    INSERT INTO knowledge_portal_config_version
        (tenant_id,site_id,version_no,status,schema_version,config_json,checksum,published_at)
    SELECT d.tenant_id,d.site_id,1,'published',2,
      jsonb_build_object(
        'schemaVersion',2,
        'revision','v19-default-1',
        'site',jsonb_build_object('siteKey',d.site_key,'scenario','party','name',d.name,'locale','zh-CN'),
        'shell',jsonb_build_object(
          'header',jsonb_build_object('showSearch',true,'showTenant',true),
          'navigation',jsonb_build_array(
            jsonb_build_object('id','home','label','首页','target','home'),
            jsonb_build_object('id','library','label','资料中心','target','library'),
            jsonb_build_object('id','ask','label','AI 问答','target','ask'),
            jsonb_build_object('id','topics','label','专题学习','target','topics')
          ),
          'footer',jsonb_build_object('text','内部知识服务 · 引用请核对现行效力')
        ),
        'theme',jsonb_build_object('preset','emerald','mode','light','density','compact','tokens',jsonb_build_object(),'customCss',''),
        'modules',jsonb_build_object(),
        'contentScope',jsonb_build_object(
          'allSpaces',true,'spaceCodes',jsonb_build_array(),'topicCodes',jsonb_build_array(),
          'contentTypes',jsonb_build_array(),'validityStatuses',jsonb_build_array('effective','pending')
        ),
        'search',jsonb_build_object('placeholder','搜索标题、正文或文号','hotKeywords',jsonb_build_array(),'defaultMode','hybrid'),
        'assistant',jsonb_build_object('enabled',true,'title','AI 知识助手','welcomeText',d.help_text,'suggestedQuestions',jsonb_build_array()),
        'pages',jsonb_build_object(
          'home',jsonb_build_object(
            'slug','home','layout','twelve-grid',
            'regions',jsonb_build_object('main',jsonb_build_array(
              jsonb_build_object('id','hero','type','hero-search','enabled',true,'variant','compact','span',12),
              jsonb_build_object('id','categories','type','category-grid','enabled',true,'variant','cards','span',12),
              jsonb_build_object('id','recent','type','recent-documents','enabled',true,'variant','list','span',8,'props',jsonb_build_object('limit',8)),
              jsonb_build_object('id','topic','type','current-topic','enabled',true,'variant','card','span',4),
              jsonb_build_object('id','history','type','reading-history','enabled',true,'variant','compact','span',4,'props',jsonb_build_object('limit',5)),
              jsonb_build_object('id','favorites','type','favorites','enabled',true,'variant','compact','span',4,'props',jsonb_build_object('limit',5))
            ))
          )
        )
      ),
      md5(d.tenant_id || ':' || d.site_id || ':v19-default-1'),
      now()
    FROM defaults d
    ON CONFLICT (tenant_id,site_id,version_no) DO NOTHING
    RETURNING tenant_id,site_id,config_version_id
)
UPDATE knowledge_portal_site s
SET current_published_version_id=i.config_version_id,update_time=now()
FROM inserted i
WHERE s.tenant_id=i.tenant_id AND s.site_id=i.site_id;

INSERT INTO knowledge_portal_site_scope(tenant_id,site_id,config_version_id,scope_type,scope_value)
SELECT s.tenant_id,s.site_id,s.current_published_version_id,'all','*'
FROM knowledge_portal_site s
WHERE s.current_published_version_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_portal_site_scope(tenant_id,site_id,config_version_id,scope_type,scope_value)
SELECT s.tenant_id,s.site_id,s.current_published_version_id,'validity',v.status
FROM knowledge_portal_site s CROSS JOIN (VALUES ('effective'),('pending')) AS v(status)
WHERE s.current_published_version_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO kma_permission
    (permission_code,name,parent_code,permission_type,permission_scope,description,sort_order,enabled,assignable)
VALUES
    ('portal-site:read','查看门户站点','content:read','menu','tenant','查看门户站点、版本和发布状态',1720,TRUE,TRUE),
    ('portal-site:create','创建门户站点','portal-site:read','action','tenant','创建场景化知识门户',1721,TRUE,TRUE),
    ('portal-site:update','编辑门户站点','portal-site:read','action','tenant','修改站点基础信息和草稿',1722,TRUE,TRUE),
    ('portal-site:delete','删除门户站点','portal-site:read','action','tenant','删除非默认且无活动发布的站点',1723,TRUE,TRUE),
    ('portal-page:edit','编排门户页面','portal-site:read','action','tenant','编辑页面、布局、导航和受控区块',1724,TRUE,TRUE),
    ('portal-theme:manage','管理门户主题','portal-site:read','action','tenant','管理语义 Token 与受控 CSS',1725,TRUE,TRUE),
    ('portal-asset:manage','管理门户资产','portal-site:read','action','tenant','上传和清理门户品牌资源',1726,TRUE,TRUE),
    ('portal-site:review','审核门户配置','portal-site:read','action','tenant','审核站点配置版本',1727,TRUE,TRUE),
    ('portal-site:publish','发布门户配置','portal-site:read','action','tenant','发布、回滚站点配置版本',1728,TRUE,TRUE),
    ('portal-analytics:read','查看门户分析','portal-site:read','menu','tenant','查看站点访问、搜索和问答分析',1729,TRUE,TRUE)
ON CONFLICT (permission_code) DO UPDATE SET
    name=EXCLUDED.name,parent_code=EXCLUDED.parent_code,permission_type=EXCLUDED.permission_type,
    permission_scope=EXCLUDED.permission_scope,description=EXCLUDED.description,sort_order=EXCLUDED.sort_order,
    enabled=TRUE,assignable=TRUE;

INSERT INTO kma_role_permission(tenant_id,role_id,permission_code)
SELECT r.tenant_id,r.role_id,p.permission_code
FROM kma_role r
JOIN kma_permission p ON p.permission_code LIKE 'portal-%'
WHERE r.role_code IN ('kma-admin','tenant-admin')
ON CONFLICT DO NOTHING;

WITH role_targets(role_code,permission_code) AS (VALUES
    ('knowledge-admin','portal-site:read'),('knowledge-admin','portal-site:create'),
    ('knowledge-admin','portal-site:update'),('knowledge-admin','portal-page:edit'),
    ('knowledge-admin','portal-theme:manage'),('knowledge-admin','portal-asset:manage'),
    ('knowledge-admin','portal-site:review'),('knowledge-admin','portal-site:publish'),
    ('knowledge-admin','portal-analytics:read'),
    ('auditor','portal-site:read'),('auditor','portal-analytics:read')
)
INSERT INTO kma_role_permission(tenant_id,role_id,permission_code)
SELECT r.tenant_id,r.role_id,t.permission_code
FROM role_targets t
JOIN kma_role r ON r.role_code=t.role_code
ON CONFLICT DO NOTHING;

COMMENT ON TABLE knowledge_portal_site IS '租户内独立知识门户站点；授权租户仍以 JWT tenant_id 为准';
COMMENT ON TABLE knowledge_portal_config_version IS 'CMS V2 草稿、审核、发布与回滚版本';
COMMENT ON TABLE knowledge_portal_site_scope IS '发布时从配置编译的站点内容范围，门户查询必须与 RBAC/ACL 取交集';
