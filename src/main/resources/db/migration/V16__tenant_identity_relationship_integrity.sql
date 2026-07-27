-- 修复历史关联中的租户标识，并用复合外键阻止跨租户用户、角色和令牌关系。
INSERT INTO kma_user_role(tenant_id,user_id,role_id)
SELECT u.tenant_id,ur.user_id,ur.role_id
FROM kma_user_role ur
JOIN kma_user u ON u.user_id=ur.user_id
JOIN kma_role r ON r.role_id=ur.role_id AND r.tenant_id=u.tenant_id
WHERE ur.tenant_id<>u.tenant_id OR ur.tenant_id<>r.tenant_id
ON CONFLICT DO NOTHING;

DELETE FROM kma_user_role ur
USING kma_user u,kma_role r
WHERE ur.user_id=u.user_id AND ur.role_id=r.role_id
  AND (ur.tenant_id<>u.tenant_id OR ur.tenant_id<>r.tenant_id);

INSERT INTO kma_role_permission(tenant_id,role_id,permission_code)
SELECT r.tenant_id,rp.role_id,rp.permission_code
FROM kma_role_permission rp
JOIN kma_role r ON r.role_id=rp.role_id
WHERE rp.tenant_id<>r.tenant_id
ON CONFLICT DO NOTHING;

DELETE FROM kma_role_permission rp
USING kma_role r
WHERE rp.role_id=r.role_id AND rp.tenant_id<>r.tenant_id;

UPDATE kma_refresh_token rt
SET tenant_id=u.tenant_id,revoked_at=COALESCE(rt.revoked_at,now())
FROM kma_user u
WHERE rt.user_id=u.user_id AND rt.tenant_id<>u.tenant_id;

ALTER TABLE kma_role
    ADD CONSTRAINT uk_kma_role_tenant_id UNIQUE (tenant_id,role_id);

ALTER TABLE kma_user_role
    DROP CONSTRAINT kma_user_role_user_id_fkey,
    DROP CONSTRAINT kma_user_role_role_id_fkey,
    ADD CONSTRAINT fk_kma_user_role_user_tenant
        FOREIGN KEY (tenant_id,user_id) REFERENCES kma_user(tenant_id,user_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_kma_user_role_role_tenant
        FOREIGN KEY (tenant_id,role_id) REFERENCES kma_role(tenant_id,role_id) ON DELETE CASCADE;

ALTER TABLE kma_role_permission
    DROP CONSTRAINT kma_role_permission_role_id_fkey,
    ADD CONSTRAINT fk_kma_role_permission_role_tenant
        FOREIGN KEY (tenant_id,role_id) REFERENCES kma_role(tenant_id,role_id) ON DELETE CASCADE;

ALTER TABLE kma_refresh_token
    DROP CONSTRAINT kma_refresh_token_user_id_fkey,
    ADD CONSTRAINT fk_kma_refresh_token_user_tenant
        FOREIGN KEY (tenant_id,user_id) REFERENCES kma_user(tenant_id,user_id) ON DELETE CASCADE;

-- 活跃用户始终至少归属于租户根组织；组织 ACL 授予 root 时可覆盖全部活跃成员。
INSERT INTO kma_user_org(tenant_id,user_id,org_id,primary_org)
SELECT u.tenant_id,u.user_id,o.org_id,TRUE
FROM kma_user u
JOIN kma_org o ON o.tenant_id=u.tenant_id AND o.org_code='root' AND o.status='active'
WHERE u.status='active'
  AND NOT EXISTS (
      SELECT 1 FROM kma_user_org uo
      WHERE uo.tenant_id=u.tenant_id AND uo.user_id=u.user_id
  )
ON CONFLICT DO NOTHING;

WITH missing_primary AS (
    SELECT uo.tenant_id,uo.user_id,min(uo.org_id) org_id
    FROM kma_user_org uo
    JOIN kma_user u ON u.tenant_id=uo.tenant_id AND u.user_id=uo.user_id
    WHERE u.status='active'
      AND NOT EXISTS (
          SELECT 1 FROM kma_user_org current_primary
          WHERE current_primary.tenant_id=uo.tenant_id
            AND current_primary.user_id=uo.user_id
            AND current_primary.primary_org
      )
    GROUP BY uo.tenant_id,uo.user_id
)
UPDATE kma_user_org uo SET primary_org=TRUE
FROM missing_primary p
WHERE uo.tenant_id=p.tenant_id AND uo.user_id=p.user_id AND uo.org_id=p.org_id;
