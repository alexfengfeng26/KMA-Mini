package com.kma.common.security;

import com.kma.common.exception.KmaException;
import com.kma.common.result.PageResult;
import com.kma.common.security.dto.UserCreateRequest;
import com.kma.common.security.dto.RoleUpsertRequest;
import com.kma.common.security.dto.UserRolesRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/** 用户管理应用服务，Controller 不直接访问持久化组件。 */
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class KmaUserAdminService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService audit;
    private final SpaceAdministrationGuard administrationGuard;

    public KmaUserAdminService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                               PasswordEncoder passwordEncoder) {
        this(jdbc, passwordEncoder, null, null);
    }

    public KmaUserAdminService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                               PasswordEncoder passwordEncoder,
                               SecurityAuditService audit) {
        this(jdbc, passwordEncoder, audit, null);
    }

    @Autowired
    public KmaUserAdminService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                               PasswordEncoder passwordEncoder,
                               SecurityAuditService audit,
                               SpaceAdministrationGuard administrationGuard) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.administrationGuard = administrationGuard;
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
            SELECT u.user_id, u.username, u.display_name, u.identity_provider, u.status,
                   u.must_change_password, u.last_login_time, u.create_time,
                   COALESCE(string_agg(DISTINCT r.role_code, ',' ORDER BY r.role_code), '') AS roles,
                   COALESCE(string_agg(DISTINCT o.name, ',' ORDER BY o.name), '') AS organizations
            FROM kma_user u
            LEFT JOIN kma_user_role ur ON ur.user_id = u.user_id
            LEFT JOIN kma_role r ON r.role_id = ur.role_id
            LEFT JOIN kma_user_org uo ON uo.user_id=u.user_id
            LEFT JOIN kma_org o ON o.org_id=uo.org_id
            GROUP BY u.user_id ORDER BY u.create_time DESC
            """);
    }

    public PageResult<Map<String, Object>> page(int pageNum, int pageSize, String keyword,
                                                String sortBy, String sortOrder) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String pattern = "%" + normalizedKeyword + "%";
        String orderColumn = switch (sortBy) {
            case "username" -> "u.username";
            case "displayName" -> "u.display_name";
            case "status" -> "u.status";
            default -> "u.create_time";
        };
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM kma_user u
            WHERE (?='' OR u.username ILIKE ? OR COALESCE(u.display_name,'') ILIKE ?)
            """, Long.class, normalizedKeyword, pattern, pattern);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT u.user_id, u.username, u.display_name, u.identity_provider, u.status,
                   u.must_change_password, u.last_login_time, u.create_time,
                   COALESCE(string_agg(DISTINCT r.role_code, ',' ORDER BY r.role_code), '') AS roles,
                   COALESCE(string_agg(DISTINCT o.name, ',' ORDER BY o.name), '') AS organizations
            FROM kma_user u
            LEFT JOIN kma_user_role ur ON ur.user_id=u.user_id
            LEFT JOIN kma_role r ON r.role_id=ur.role_id
            LEFT JOIN kma_user_org uo ON uo.user_id=u.user_id
            LEFT JOIN kma_org o ON o.org_id=uo.org_id
            WHERE (?='' OR u.username ILIKE ? OR COALESCE(u.display_name,'') ILIKE ?)
            GROUP BY u.user_id
            ORDER BY %s %s, u.user_id DESC
            LIMIT ? OFFSET ?
            """.formatted(orderColumn, direction), normalizedKeyword, pattern, pattern,
            pageSize, (pageNum - 1) * pageSize);
        return new PageResult<>(rows, total == null ? 0 : total, pageNum, pageSize);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public Long create(UserCreateRequest request) {
        validateAssignableRoles(request.getRoles());
        Long userId;
        try {
            userId = jdbc.queryForObject("""
                INSERT INTO kma_user(username, display_name, password_hash,
                                     identity_provider, status, must_change_password)
                VALUES (?, ?, ?, 'local', 'active', true) RETURNING user_id
                """, Long.class, request.getUsername(), request.getDisplayName(),
                passwordEncoder.encode(request.getInitialPassword()));
        } catch (DataIntegrityViolationException ex) {
            throw new KmaException(409, "用户名已存在或账号数据无效");
        }
        for (String role : request.getRoles()) {
            int assigned = jdbc.update("""
                INSERT INTO kma_user_role(user_id, role_id)
                SELECT ?, role_id FROM kma_role WHERE role_code = ?
                ON CONFLICT DO NOTHING
                """, userId, role);
            if (assigned == 0) {
                throw new KmaException(400, "角色不存在或已分配: " + role);
            }
        }
        int organizationAssigned = jdbc.update("""
            INSERT INTO kma_user_org(user_id,org_id,primary_org)
            SELECT ?,org_id,TRUE FROM kma_org
            WHERE org_code='root' AND status='active'
            """, userId);
        if (organizationAssigned != 1) throw new KmaException(500, "根组织不存在或不可用");
        record("user.create", "user:" + userId, Map.of("username", request.getUsername(), "roles", request.getRoles()));
        return userId;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void changeStatus(Long userId, String status) {
        if ("disabled".equals(status)) {
            assertNotLastApplicationAdmin(userId);
            if (administrationGuard != null) {
                administrationGuard.assertPrincipalCanBeDisabled("user",
                    String.valueOf(userId));
            }
        }
        int rows = jdbc.update("""
            UPDATE kma_user SET status = ?, auth_version=auth_version+1, update_time = now()
            WHERE user_id = ?
            """, status, userId);
        if (rows == 0) {
            throw new KmaException(404, "用户不存在");
        }
        if ("active".equals(status)) {
            int organizations = jdbc.update("""
                INSERT INTO kma_user_org(user_id,org_id,primary_org)
                SELECT ?,org_id,TRUE FROM kma_org root
                WHERE root.org_code='root' AND root.status='active'
                  AND NOT EXISTS (
                      SELECT 1 FROM kma_user_org current_org
                      WHERE current_org.user_id=?
                  )
                ON CONFLICT DO NOTHING
                """, userId, userId);
            if (organizations > 0) record("user.organization.default", "user:" + userId, Map.of("organization", "root"));
        }
        if ("disabled".equals(status)) revokeRefreshTokens(userId);
        if (administrationGuard != null) {
            administrationGuard.assertOperationalAdmins();
        }
        record("user.status.update", "user:" + userId, Map.of("status", status));
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void resetPassword(Long userId, String newPassword) {
        int rows = jdbc.update("""
            UPDATE kma_user SET password_hash = ?, must_change_password = true,
                auth_version=auth_version+1, update_time = now()
            WHERE user_id = ? AND identity_provider = 'local'
            """, passwordEncoder.encode(newPassword), userId);
        if (rows == 0) {
            throw new KmaException(404, "本地用户不存在");
        }
        jdbc.update("""
            UPDATE kma_refresh_token SET revoked_at = now()
            WHERE user_id = ? AND revoked_at IS NULL
            """, userId);
        record("user.password.reset", "user:" + userId, Map.of("mustChangePassword", true));
    }

    public List<Map<String, Object>> listRoles() {
        return jdbc.queryForList("""
            SELECT r.role_id,r.role_code,r.name,r.built_in,
                   COALESCE(string_agg(rp.permission_code,',' ORDER BY rp.permission_code),'') permissions
            FROM kma_role r LEFT JOIN kma_role_permission rp ON rp.role_id=r.role_id
            GROUP BY r.role_id ORDER BY r.role_code
            """);
    }

    public List<Map<String, Object>> listPermissions() {
        return jdbc.queryForList("SELECT permission_code,name FROM kma_permission ORDER BY permission_code");
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void updateRoles(Long userId, UserRolesRequest request) {
        validateAssignableRoles(request.getRoles());
        Integer users = jdbc.queryForObject("SELECT count(*) FROM kma_user WHERE user_id=?",
            Integer.class, userId);
        if (users == null || users == 0) throw new KmaException(404, "用户不存在");
        if (!request.getRoles().contains("kma-admin")) assertNotLastApplicationAdmin(userId);
        jdbc.update("DELETE FROM kma_user_role WHERE user_id=?", userId);
        for (String role : request.getRoles()) {
            int rows = jdbc.update("""
                INSERT INTO kma_user_role(user_id,role_id)
                SELECT ?,role_id FROM kma_role WHERE role_code=?
                """, userId, role);
            if (rows != 1) throw new KmaException(400, "角色不存在: " + role);
        }
        jdbc.update("UPDATE kma_user SET auth_version=auth_version+1,update_time=now() WHERE user_id=?", userId);
        if (administrationGuard != null) administrationGuard.assertOperationalAdmins();
        record("user.roles.update", "user:" + userId, Map.of("roles", request.getRoles()));
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    @Deprecated(forRemoval = true)
    public Long upsertRole(RoleUpsertRequest request) {
        if ("kma-admin".equals(request.getRoleCode()) || request.getPermissions().contains("kma:admin")) {
            throw new KmaException(403, "系统管理员角色不可通过兼容接口维护");
        }
        Integer validPermissions = jdbc.queryForObject("""
            SELECT count(*) FROM kma_permission
            WHERE enabled=TRUE AND assignable=TRUE
              AND permission_code = ANY (string_to_array(?, ','))
            """, Integer.class, String.join(",", request.getPermissions()));
        if (validPermissions == null || validPermissions != request.getPermissions().size()) {
            throw new KmaException(403, "包含不存在或不可分配的权限");
        }
        Long roleId = jdbc.queryForObject("""
            INSERT INTO kma_role(role_code,name,built_in) VALUES (?,?,FALSE)
            ON CONFLICT (role_code) DO UPDATE SET name=EXCLUDED.name,update_time=now()
            RETURNING role_id
            """, Long.class, request.getRoleCode(), request.getName());
        jdbc.update("DELETE FROM kma_role_permission WHERE role_id=?", roleId);
        for (String permission : request.getPermissions()) {
            int rows = jdbc.update("""
                INSERT INTO kma_role_permission(role_id,permission_code)
                SELECT ?,permission_code FROM kma_permission WHERE permission_code=?
                """, roleId, permission);
            if (rows != 1) throw new KmaException(400, "权限不存在: " + permission);
        }
        record("role.compat.upsert", "role:" + roleId,
            Map.of("roleCode", request.getRoleCode(), "permissions", request.getPermissions()));
        return roleId;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void revokeTokens(Long userId) {
        revokeRefreshTokens(userId);
        Integer users = jdbc.queryForObject("SELECT count(*) FROM kma_user WHERE user_id=?",
            Integer.class, userId);
        if (users == null || users == 0) throw new KmaException(404, "用户不存在");
        jdbc.update("UPDATE kma_user SET auth_version=auth_version+1,update_time=now() WHERE user_id=?", userId);
        record("user.tokens.revoke", "user:" + userId, Map.of());
    }

    private void revokeRefreshTokens(Long userId) {
        jdbc.update("""
            UPDATE kma_refresh_token SET revoked_at=now()
            WHERE user_id=? AND revoked_at IS NULL
            """, userId);
    }

    private void validateAssignableRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) return;
        List<Map<String, Object>> found = jdbc.queryForList("""
            SELECT r.role_code, bool_or(rp.permission_code='kma:admin') application_admin_role
            FROM kma_role r
            LEFT JOIN kma_role_permission rp ON rp.role_id=r.role_id
            LEFT JOIN kma_permission p ON p.permission_code=rp.permission_code
            WHERE r.status='active' AND r.role_code = ANY (string_to_array(?, ','))
            GROUP BY r.role_id
            """, String.join(",", roles));
        if (found.size() != roles.size()) throw new KmaException(400, "包含不存在或已停用的角色");
        boolean applicationAdminRole = found.stream()
            .anyMatch(row -> Boolean.TRUE.equals(row.get("application_admin_role")));
        if (applicationAdminRole && !KmaIdentityContext.isSuperAdmin()) {
            throw new KmaException(403, "只有系统管理员可以分配系统管理员角色");
        }
    }

    private void assertNotLastApplicationAdmin(Long userId) {
        if (administrationGuard != null) {
            administrationGuard.assertNotLastApplicationAdmin(userId);
            return;
        }
        Integer targetIsAdmin = jdbc.queryForObject("""
            SELECT count(*) FROM kma_user_role ur JOIN kma_role r
              ON r.role_id=ur.role_id
            WHERE ur.user_id=? AND r.role_code='kma-admin'
            """, Integer.class, userId);
        if (targetIsAdmin == null || targetIsAdmin == 0) return;
        Integer remaining = jdbc.queryForObject("""
            SELECT count(DISTINCT u.user_id) FROM kma_user u
            JOIN kma_user_role ur ON ur.user_id=u.user_id
            JOIN kma_role r ON r.role_id=ur.role_id
            WHERE u.status='active' AND u.user_id<>? AND r.role_code='kma-admin'
            """, Integer.class, userId);
        if (remaining == null || remaining == 0) throw new KmaException(409, "LAST_ADMIN_REQUIRED");
    }

    private void record(String action, String resource, Map<String, Object> details) {
        if (audit != null) audit.recordRequired("identity_change", "warning", action, resource,
            Map.of(), details, details);
    }
}
