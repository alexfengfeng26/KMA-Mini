-- A portal site may own multiple independent Theme V4 packages.  The published
-- portal configuration remains the single authoritative selector.

ALTER TABLE knowledge_portal_theme
    DROP CONSTRAINT IF EXISTS uk_portal_theme_site;

ALTER TABLE knowledge_portal_theme
    DROP CONSTRAINT IF EXISTS uk_portal_theme_key;

ALTER TABLE knowledge_portal_theme
    ADD CONSTRAINT uk_portal_theme_site_key UNIQUE (site_id, theme_key);

COMMENT ON TABLE knowledge_portal_theme IS
    'Portal Theme V4 catalog: a site can own several independently versioned themes';
