-- KMA-FRONTEND.4：平台签名门户扩展目录与站点版本使用记录。

CREATE TABLE portal_extension_release (
    extension_id VARCHAR(64) NOT NULL,
    extension_version VARCHAR(32) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    entry_url VARCHAR(512) NOT NULL,
    integrity_hash VARCHAR(128) NOT NULL,
    manifest_json JSONB NOT NULL,
    signature VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    min_frontend_version VARCHAR(32),
    created_by BIGINT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (extension_id, extension_version),
    CONSTRAINT ck_portal_extension_status CHECK (status IN ('active','retired','revoked')),
    CONSTRAINT ck_portal_extension_entry CHECK (entry_url ~ '^/portal-extensions/[a-z][a-z0-9_-]{1,63}/[A-Za-z0-9._-]+/index[.]html$')
);
CREATE INDEX idx_portal_extension_catalog ON portal_extension_release(status, extension_id, extension_version);

CREATE TABLE knowledge_portal_extension_usage (
    tenant_id VARCHAR(64) NOT NULL REFERENCES kma_tenant(tenant_id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL,
    config_version_id BIGINT NOT NULL,
    page_slug VARCHAR(64) NOT NULL,
    region VARCHAR(16) NOT NULL,
    extension_id VARCHAR(64) NOT NULL,
    extension_version VARCHAR(32) NOT NULL,
    slot_key VARCHAR(64) NOT NULL,
    extension_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, site_id, config_version_id, page_slug, region, extension_id, extension_version, slot_key),
    CONSTRAINT fk_portal_extension_usage_site FOREIGN KEY (tenant_id, site_id)
        REFERENCES knowledge_portal_site(tenant_id, site_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_extension_usage_version FOREIGN KEY (tenant_id, config_version_id)
        REFERENCES knowledge_portal_config_version(tenant_id, config_version_id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_extension_usage_release FOREIGN KEY (extension_id, extension_version)
        REFERENCES portal_extension_release(extension_id, extension_version) ON DELETE RESTRICT,
    CONSTRAINT ck_portal_extension_usage_region CHECK (region IN ('header','main','sidebar','footer'))
);
CREATE INDEX idx_portal_extension_usage_release ON knowledge_portal_extension_usage(extension_id, extension_version);

INSERT INTO portal_extension_release
    (extension_id,extension_version,display_name,entry_url,integrity_hash,manifest_json,signature,status,min_frontend_version)
VALUES
    ('portal-showcase','1.0.0','站点洞察展示组件','/portal-extensions/portal-showcase/1.0.0/index.html',
     'builtin:portal-showcase-1.0.0',
     '{
       "id":"portal-showcase","version":"1.0.0","displayName":"站点洞察展示组件",
       "slots":["header","main","sidebar","footer"],
       "capabilities":["page-context","contents","search","analytics"],
       "settingsSchema":{"type":"object","additionalProperties":false,"properties":{"title":{"type":"string","maxLength":80},"limit":{"type":"integer","minimum":1,"maximum":10}}}
     }'::jsonb,
     'builtin', 'active', '0.2.0')
ON CONFLICT (extension_id,extension_version) DO NOTHING;

INSERT INTO kma_permission
    (permission_code,name,parent_code,permission_type,permission_scope,description,sort_order,enabled,assignable)
VALUES
    ('portal-extension:read','查看门户扩展目录','portal-site:read','action','tenant','查看平台已发布的门户扩展包',1730,TRUE,TRUE),
    ('portal-extension:release','发布门户扩展包',NULL,'action','platform','仅平台 CI 服务身份可登记签名扩展包',1731,TRUE,FALSE)
ON CONFLICT (permission_code) DO UPDATE SET
    name=EXCLUDED.name,parent_code=EXCLUDED.parent_code,permission_type=EXCLUDED.permission_type,
    permission_scope=EXCLUDED.permission_scope,description=EXCLUDED.description,sort_order=EXCLUDED.sort_order,
    enabled=TRUE,assignable=EXCLUDED.assignable;

INSERT INTO kma_role_permission(tenant_id,role_id,permission_code)
SELECT r.tenant_id,r.role_id,'portal-extension:read'
FROM kma_role r
WHERE r.role_code IN ('kma-admin','tenant-admin','knowledge-admin')
ON CONFLICT DO NOTHING;

-- Upgrade every currently published V19 site to an explicit visual pack without changing its content scope.
UPDATE knowledge_portal_config_version v
SET config_json = jsonb_set(
        jsonb_set(
            jsonb_set(v.config_json, '{theme,pack}', to_jsonb(CASE s.scenario
                WHEN 'internal-policy' THEN 'policy-workbench'
                WHEN 'product-help' THEN 'help-product'
                ELSE 'party-authority' END), TRUE),
            '{shell,layout}', to_jsonb(CASE s.scenario
                WHEN 'internal-policy' THEN 'sidebar-workbench'
                WHEN 'product-help' THEN 'search-center'
                ELSE 'editorial-authority' END), TRUE),
        '{pages,home,extensions}', CASE WHEN s.site_key='help-demo' THEN jsonb_build_array(
            jsonb_build_object('extensionId','portal-showcase','version','1.0.0','slotKey','site-insight',
                'region','main','enabled',TRUE,'config',jsonb_build_object('title','帮助中心洞察','limit',3))
        ) ELSE '[]'::jsonb END, TRUE),
    checksum = md5(v.config_json::text || ':frontend.4')
FROM knowledge_portal_site s
WHERE s.tenant_id=v.tenant_id
  AND s.current_published_version_id=v.config_version_id;

INSERT INTO knowledge_portal_extension_usage
    (tenant_id,site_id,config_version_id,page_slug,region,extension_id,extension_version,slot_key,extension_config)
SELECT s.tenant_id,s.site_id,v.config_version_id,'home',
       COALESCE(binding.value->>'region','main'),binding.value->>'extensionId',binding.value->>'version',
       binding.value->>'slotKey',COALESCE(binding.value->'config','{}'::jsonb)
FROM knowledge_portal_site s
JOIN knowledge_portal_config_version v
  ON v.tenant_id=s.tenant_id AND v.config_version_id=s.current_published_version_id
CROSS JOIN LATERAL jsonb_array_elements(COALESCE(v.config_json #> '{pages,home,extensions}','[]'::jsonb)) binding(value)
ON CONFLICT DO NOTHING;

COMMENT ON TABLE portal_extension_release IS '平台开发者经 CI 签名后发布的门户扩展不可变目录';
COMMENT ON TABLE knowledge_portal_extension_usage IS '门户发布版本编译出的扩展引用；被引用扩展不得直接删除';
