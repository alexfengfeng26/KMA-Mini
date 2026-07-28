-- Portal Theme V4: immutable full-site themes rendered in an isolated browser sandbox.

ALTER TABLE knowledge_portal_config_version
    DROP CONSTRAINT ck_portal_config_schema,
    ADD CONSTRAINT ck_portal_config_schema CHECK (schema_version IN (2,3,4));

CREATE TABLE knowledge_portal_theme (
    theme_id BIGSERIAL PRIMARY KEY,
    site_id BIGINT NOT NULL REFERENCES knowledge_portal_site(site_id) ON DELETE CASCADE,
    theme_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    current_version_id BIGINT,
    created_by BIGINT REFERENCES kma_user(user_id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES kma_user(user_id) ON DELETE SET NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_portal_theme_site UNIQUE (site_id),
    CONSTRAINT uk_portal_theme_key UNIQUE (theme_key),
    CONSTRAINT ck_portal_theme_key CHECK (theme_key ~ '^[a-z][a-z0-9_-]{1,63}$'),
    CONSTRAINT ck_portal_theme_status CHECK (status IN ('active','disabled'))
);

CREATE TABLE knowledge_portal_theme_version (
    theme_version_id BIGSERIAL PRIMARY KEY,
    theme_id BIGINT NOT NULL REFERENCES knowledge_portal_theme(theme_id) ON DELETE CASCADE,
    version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'draft',
    manifest_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    compiled_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    checksum VARCHAR(64) NOT NULL,
    scan_status VARCHAR(16) NOT NULL DEFAULT 'pending',
    scan_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    file_count INT NOT NULL DEFAULT 0,
    expanded_size BIGINT NOT NULL DEFAULT 0,
    lock_version INT NOT NULL DEFAULT 0,
    created_by BIGINT REFERENCES kma_user(user_id) ON DELETE SET NULL,
    published_by BIGINT REFERENCES kma_user(user_id) ON DELETE SET NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    scanned_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    CONSTRAINT uk_portal_theme_version UNIQUE (theme_id,version_no),
    CONSTRAINT ck_portal_theme_version_status CHECK (status IN ('draft','published','archived')),
    CONSTRAINT ck_portal_theme_scan_status CHECK (scan_status IN ('pending','passed','failed')),
    CONSTRAINT ck_portal_theme_limits CHECK (
        version_no > 0 AND file_count BETWEEN 0 AND 100
        AND expanded_size BETWEEN 0 AND 5242880
    )
);

ALTER TABLE knowledge_portal_theme
    ADD CONSTRAINT fk_portal_theme_current_version
    FOREIGN KEY (current_version_id) REFERENCES knowledge_portal_theme_version(theme_version_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE knowledge_portal_theme_file (
    theme_version_id BIGINT NOT NULL REFERENCES knowledge_portal_theme_version(theme_version_id) ON DELETE CASCADE,
    file_path VARCHAR(256) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (theme_version_id,file_path),
    CONSTRAINT ck_portal_theme_file_path CHECK (
        file_path ~ '^[A-Za-z0-9][A-Za-z0-9_./-]{0,255}$'
        AND file_path !~ '(^|/)[.][.](/|$)'
    ),
    CONSTRAINT ck_portal_theme_file_size CHECK (size_bytes BETWEEN 0 AND 1048576)
);

CREATE TABLE knowledge_portal_theme_usage (
    site_id BIGINT NOT NULL REFERENCES knowledge_portal_site(site_id) ON DELETE CASCADE,
    config_version_id BIGINT NOT NULL REFERENCES knowledge_portal_config_version(config_version_id) ON DELETE CASCADE,
    theme_id BIGINT NOT NULL REFERENCES knowledge_portal_theme(theme_id) ON DELETE RESTRICT,
    theme_version_id BIGINT NOT NULL REFERENCES knowledge_portal_theme_version(theme_version_id) ON DELETE RESTRICT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (site_id,config_version_id)
);

CREATE INDEX idx_portal_theme_version
    ON knowledge_portal_theme_version(theme_id,status,version_no DESC);
CREATE INDEX idx_portal_theme_usage_version
    ON knowledge_portal_theme_usage(theme_version_id);

-- Create one default full-site theme per site. The known-safe bundled source is validated again by the application.
INSERT INTO knowledge_portal_theme(site_id,theme_key,display_name,description,created_by,updated_by)
SELECT s.site_id, left(s.site_key || '-theme',64), s.name || ' 全站主题',
       '由 V23 从已发布门户自动转换的 Portal Theme V4', s.updated_by, s.updated_by
FROM knowledge_portal_site s
ON CONFLICT (site_id) DO NOTHING;

INSERT INTO knowledge_portal_theme_version
    (theme_id,version_no,status,manifest_json,compiled_json,checksum,scan_status,scan_result,
     file_count,expanded_size,created_by,published_by,scanned_at,published_at)
SELECT t.theme_id,1,'published',
       '{"kind":"portal-theme","capabilities":["page-context","contents","search","ask","analytics","navigation"],"entry":"layout.html"}'::jsonb,
       '{"compiler":"kma-liquid-v1","validated":true}'::jsonb,
       md5(t.theme_key || ':v1'), 'passed', '{"issues":[],"migration":"V23"}'::jsonb,
       11,0,t.created_by,t.created_by,now(),now()
FROM knowledge_portal_theme t
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_portal_theme_version v WHERE v.theme_id=t.theme_id
);

UPDATE knowledge_portal_theme t
SET current_version_id=v.theme_version_id,update_time=now()
FROM knowledge_portal_theme_version v
WHERE v.theme_id=t.theme_id AND v.version_no=1 AND t.current_version_id IS NULL;

WITH source_files(file_path,mime_type,content) AS (
    VALUES
    ('layout.html','text/html;charset=UTF-8',$kma$
<div class="kma-site">
  <header class="site-header">
    <a class="brand" data-kma-nav="home">{{ site.name }}</a>
    <nav>
      <a data-kma-nav="home">首页</a><a data-kma-nav="library">资料中心</a>
      <a data-kma-nav="topics">专题目录</a><a data-kma-nav="ask">AI 问答</a>
      <a data-kma-nav="favorites">我的收藏</a><a data-kma-nav="profile">个人中心</a>
    </nav>
  </header>
  <main>{% slot content %}</main>
  <footer>由 KMA Mini 提供知识服务 · Portal Theme V4</footer>
</div>$kma$),
    ('pages/home.html','text/html;charset=UTF-8',$kma$
<section class="hero"><span>知识服务门户</span><h1>{{ site.name }}</h1><p>统一检索、专题学习与可信 AI 问答</p>
<button data-kma-nav="library">浏览资料</button><button data-kma-nav="ask">开始提问</button></section>
<section><h2>最新资料</h2><kma-widget name="content-list"></kma-widget></section>
<section><h2>专题学习</h2><kma-widget name="topic-directory"></kma-widget></section>$kma$),
    ('pages/library.html','text/html;charset=UTF-8',$kma$<section><h1>资料中心</h1><kma-widget name="content-list"></kma-widget></section>$kma$),
    ('pages/topics.html','text/html;charset=UTF-8',$kma$<section><h1>专题目录</h1><kma-widget name="topic-directory"></kma-widget></section>$kma$),
    ('pages/ask.html','text/html;charset=UTF-8',$kma$<section><h1>AI 知识问答</h1><kma-widget name="ai-chat"></kma-widget></section>$kma$),
    ('pages/content.html','text/html;charset=UTF-8',$kma$<section><kma-widget name="document-reader"></kma-widget></section>$kma$),
    ('pages/search.html','text/html;charset=UTF-8',$kma$<section><h1>搜索结果</h1><kma-widget name="content-list"></kma-widget></section>$kma$),
    ('pages/favorites.html','text/html;charset=UTF-8',$kma$<section><h1>我的收藏</h1><kma-widget name="favorite-list"></kma-widget></section>$kma$),
    ('pages/profile.html','text/html;charset=UTF-8',$kma$<section><h1>个人中心</h1><kma-widget name="profile-card"></kma-widget></section>$kma$),
    ('styles/theme.css','text/css;charset=UTF-8',$kma$
:root{font-family:"Microsoft YaHei",system-ui,sans-serif;color:#17332d;background:#f5f8f7}
*{box-sizing:border-box}body{margin:0}.kma-site{min-height:100vh;display:grid;grid-template-rows:auto 1fr auto}
.site-header{display:flex;align-items:center;gap:32px;padding:18px clamp(20px,4vw,72px);background:#fff;border-bottom:1px solid #dce7e3}
.brand{font-size:20px;font-weight:800;color:#9d171c;cursor:pointer}.site-header nav{display:flex;flex-wrap:wrap;gap:20px}
.site-header a{cursor:pointer}main{width:100%;padding:clamp(22px,4vw,64px)}section{margin:0 0 42px}
.hero{padding:clamp(32px,7vw,96px);color:#fff;background:linear-gradient(135deg,#8f171c,#c93931);border-radius:24px}
.hero h1{font-size:clamp(36px,6vw,72px);margin:12px 0}.hero button{margin:20px 12px 0 0;padding:12px 22px;border:0;border-radius:10px;cursor:pointer}
.kma-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.kma-card{padding:20px;background:#fff;border:1px solid #dce7e3;border-radius:14px}
footer{padding:24px;text-align:center;color:#70827d;background:#fff;border-top:1px solid #dce7e3}
@media(width<900px){.site-header{align-items:flex-start;flex-direction:column}.kma-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(width<600px){.kma-grid{grid-template-columns:1fr}main{padding:18px}.hero{border-radius:16px}}
$kma$),
    ('scripts/theme.js','text/javascript;charset=UTF-8',$kma$
document.documentElement.dataset.themeReady='true'
$kma$)
)
INSERT INTO knowledge_portal_theme_file(theme_version_id,file_path,mime_type,size_bytes,checksum,content)
SELECT v.theme_version_id,f.file_path,f.mime_type,octet_length(convert_to(f.content,'UTF8')),
       md5(f.content),convert_to(f.content,'UTF8')
FROM knowledge_portal_theme_version v
JOIN knowledge_portal_theme t ON t.theme_id=v.theme_id
CROSS JOIN source_files f
WHERE v.version_no=1
ON CONFLICT DO NOTHING;

-- Auto-convert and publish in the same Flyway transaction. Old V2/V3 versions remain archived and rollbackable.
WITH current_config AS (
    SELECT s.site_id,s.site_key,s.current_published_version_id,cv.config_json,cv.created_by,
           t.theme_id,t.current_version_id AS theme_version_id,
           (SELECT COALESCE(max(x.version_no),0)+1 FROM knowledge_portal_config_version x WHERE x.site_id=s.site_id) AS next_no
    FROM knowledge_portal_site s
    JOIN knowledge_portal_config_version cv ON cv.config_version_id=s.current_published_version_id
    JOIN knowledge_portal_theme t ON t.site_id=s.site_id
    WHERE cv.schema_version IN (2,3)
), inserted AS (
    INSERT INTO knowledge_portal_config_version
        (site_id,version_no,status,schema_version,config_json,checksum,change_note,
         created_by,reviewed_by,published_by,submitted_at,reviewed_at,published_at)
    SELECT c.site_id,c.next_no,'published',4,
           jsonb_build_object(
               'schemaVersion',4,
               'revision','theme-v4-' || c.next_no,
               'site',c.config_json->'site',
               'contentScope',c.config_json->'contentScope',
               'modules',c.config_json->'modules',
               'search',c.config_json->'search',
               'assistant',c.config_json->'assistant',
               'theme',jsonb_build_object('themeId',c.theme_id,'versionId',c.theme_version_id),
               'routes',jsonb_build_object(
                   'home','pages/home.html','library','pages/library.html','topics','pages/topics.html',
                   'ask','pages/ask.html','content','pages/content.html','search','pages/search.html',
                   'favorites','pages/favorites.html','profile','pages/profile.html'
               )
           ),
           md5(c.site_key || ':theme-v4:' || c.next_no),'V23 自动转换并发布 Portal Theme V4',
           c.created_by,c.created_by,c.created_by,now(),now(),now()
    FROM current_config c
    RETURNING config_version_id,site_id
)
UPDATE knowledge_portal_site s
SET current_published_version_id=i.config_version_id,update_time=now()
FROM inserted i WHERE i.site_id=s.site_id;

UPDATE knowledge_portal_config_version cv
SET status='archived'
FROM knowledge_portal_site s
WHERE cv.site_id=s.site_id AND cv.status='published'
  AND cv.config_version_id<>s.current_published_version_id;

INSERT INTO knowledge_portal_theme_usage(site_id,config_version_id,theme_id,theme_version_id)
SELECT s.site_id,s.current_published_version_id,t.theme_id,t.current_version_id
FROM knowledge_portal_site s
JOIN knowledge_portal_theme t ON t.site_id=s.site_id
JOIN knowledge_portal_config_version cv ON cv.config_version_id=s.current_published_version_id
WHERE cv.schema_version=4
ON CONFLICT DO NOTHING;

COMMENT ON TABLE knowledge_portal_theme IS 'Portal Theme V4 full-site theme catalog';
COMMENT ON TABLE knowledge_portal_theme_version IS 'Immutable scanned full-site theme versions';
COMMENT ON TABLE knowledge_portal_theme_file IS 'Theme HTML, Liquid, CSS, isolated JavaScript and assets';
