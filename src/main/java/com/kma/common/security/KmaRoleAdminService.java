package com.kma.common.security;

import com.kma.common.exception.KmaException;
import com.kma.common.security.dto.PermissionNode;
import com.kma.common.security.dto.RoleUpsertRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class KmaRoleAdminService {
    private final JdbcTemplate jdbc;
    private final SecurityAuditService audit;
    private final SpaceAdministrationGuard administrationGuard;

    public KmaRoleAdminService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                               SecurityAuditService audit) {
        this(jdbc, audit, null);
    }

    @Autowired
    public KmaRoleAdminService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                               SecurityAuditService audit,
                               SpaceAdministrationGuard administrationGuard) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.administrationGuard = administrationGuard;
    }

    public List<Map<String, Object>> list() {
        boolean platformAdmin = KmaIdentityContext.isSuperAdmin();
        return jdbc.query("""
            SELECT r.role_id,r.role_code,r.name,r.description,r.status,r.built_in,r.create_time,r.update_time,
                   COALESCE(array_agg(rp.permission_code ORDER BY rp.permission_code)
                       FILTER (WHERE rp.permission_code IS NOT NULL),ARRAY[]::varchar[]) permissions,
                   COUNT(DISTINCT ur.user_id) AS user_count,
                   COALESCE(bool_or(rp.permission_code='kma:admin'),FALSE) AS application_admin_role
            FROM kma_role r
            LEFT JOIN kma_role_permission rp ON rp.role_id=r.role_id
            LEFT JOIN kma_permission p ON p.permission_code=rp.permission_code
            LEFT JOIN kma_user_role ur ON ur.role_id=r.role_id
            GROUP BY r.role_id ORDER BY r.built_in DESC,r.role_code
            """, (rs, row) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("roleId", rs.getLong("role_id"));
                item.put("roleCode", rs.getString("role_code"));
                item.put("name", rs.getString("name"));
                item.put("description", rs.getString("description"));
                item.put("status", rs.getString("status"));
                item.put("builtIn", rs.getBoolean("built_in"));
                String[] permissionArray = (String[]) rs.getArray("permissions").getArray();
                item.put("permissions", List.of(permissionArray));
                item.put("permissionCount", permissionArray.length);
                item.put("userCount", rs.getLong("user_count"));
                item.put("assignable", platformAdmin || !rs.getBoolean("application_admin_role"));
                return item;
            });
    }

    public List<PermissionNode> permissionTree() {
        boolean platformAdmin = KmaIdentityContext.isSuperAdmin();
        List<PermissionNodeRow> rows = jdbc.query("""
            SELECT permission_code,name,parent_code,permission_type,description,sort_order
            FROM kma_permission
            WHERE enabled=TRUE AND assignable=TRUE AND permission_type<>'legacy'
            ORDER BY sort_order,permission_code
            """, (rs, row) -> new PermissionNodeRow(rs.getString("permission_code"), rs.getString("name"),
            rs.getString("parent_code"), rs.getString("permission_type"),
            rs.getString("description"), rs.getInt("sort_order")));
        Map<String, PermissionNode> nodes = new LinkedHashMap<>();
        for (PermissionNodeRow row : rows) nodes.put(row.code(), new PermissionNode(row.code(), row.name(),
            row.type(), "application", permissionModule(row.code()), row.description(), row.sortOrder()));
        List<PermissionNode> roots = new ArrayList<>();
        for (PermissionNodeRow row : rows) {
            PermissionNode node = nodes.get(row.code());
            PermissionNode parent = nodes.get(row.parent());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public Long create(RoleUpsertRequest request) {
        if ("kma-admin".equals(request.getRoleCode())) throw new KmaException(403, "系统管理员角色不可创建");
        Set<String> permissions = normalizePermissions(request.getPermissions());
        Long roleId;
        try {
            roleId = jdbc.queryForObject("""
                INSERT INTO kma_role(role_code,name,description,status,built_in)
                VALUES (?,?,?,?,FALSE) RETURNING role_id
                """, Long.class, request.getRoleCode(), request.getName(), request.getDescription(),
                request.getStatus());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new KmaException(409, "角色编码已存在或角色数据无效");
        }
        replacePermissions(roleId, permissions);
        audit.recordRequired("role_change", "info", "role.create", "role:" + roleId, Map.of(),
            Map.of("roleCode", request.getRoleCode(), "permissions", permissions, "status", request.getStatus()),
            Map.of("roleCode", request.getRoleCode()));
        return roleId;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void update(Long roleId, RoleUpsertRequest request) {
        RoleState role = requireRole(roleId);
        if (!role.roleCode().equals(request.getRoleCode())) throw new KmaException(409, "角色编码不可修改");
        if ("kma-admin".equals(role.roleCode())) throw new KmaException(409, "系统管理员角色由启动配置维护，不允许编辑");
        if ("disabled".equals(request.getStatus()) && administrationGuard != null) {
            administrationGuard.assertPrincipalCanBeDisabled("role", role.roleCode());
        }
        Set<String> permissions = normalizePermissions(request.getPermissions());
        jdbc.update("UPDATE kma_role SET name=?,description=?,status=?,update_time=now() WHERE role_id=?",
            request.getName(), request.getDescription(), request.getStatus(), roleId);
        replacePermissions(roleId, permissions);
        invalidateRoleMembers(roleId);
        if (administrationGuard != null) administrationGuard.assertOperationalAdmins();
        audit.recordRequired("role_change", "warning", "role.update", "role:" + roleId,
            Map.of("roleCode", role.roleCode()),
            Map.of("roleCode", role.roleCode(), "permissions", permissions, "status", request.getStatus()),
            Map.of("authorizationVersionInvalidated", true));
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void delete(Long roleId) {
        RoleState role = requireRole(roleId);
        if (role.builtIn()) throw new KmaException(409, "内置角色不可删除");
        Integer users = jdbc.queryForObject("SELECT count(*) FROM kma_user_role WHERE role_id=?",
            Integer.class, roleId);
        if (users != null && users > 0) throw new KmaException(409, "角色仍被用户使用，不能删除");
        Integer acls = jdbc.queryForObject("""
            SELECT count(*) FROM knowledge_space_acl
            WHERE principal_type='role' AND principal_value=?
            """, Integer.class, role.roleCode());
        if (acls != null && acls > 0) throw new KmaException(409, "角色仍被空间 ACL 使用，不能删除");
        jdbc.update("DELETE FROM kma_role WHERE role_id=?", roleId);
        audit.recordRequired("role_change", "warning", "role.delete", "role:" + roleId,
            Map.of("roleCode", role.roleCode()), Map.of(), Map.of());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public Long clone(Long roleId) {
        RoleState source = requireRole(roleId);
        List<Map<String, Object>> sourceRows = jdbc.queryForList("""
            SELECT name,description,status FROM kma_role WHERE role_id=?
            """, roleId);
        Map<String, Object> sourceRow = sourceRows.getFirst();
        List<String> permissions = jdbc.queryForList("""
            SELECT permission_code FROM kma_role_permission WHERE role_id=?
            """, String.class, roleId);
        String baseCode = source.roleCode().replaceAll("-copy(-\\d+)?$", "");
        String newCode = baseCode + "-copy";
        int suffix = 1;
        while (jdbc.queryForObject("SELECT count(*) FROM kma_role WHERE role_code=?", Integer.class, newCode) > 0) {
            suffix++;
            newCode = baseCode + "-copy-" + suffix;
        }
        if (newCode.length() > 63) {
            newCode = newCode.substring(0, 63);
        }
        RoleUpsertRequest request = new RoleUpsertRequest();
        request.setRoleCode(newCode);
        request.setName(sourceRow.get("name") + " (复制)");
        request.setDescription((String) sourceRow.get("description"));
        request.setStatus((String) sourceRow.get("status"));
        request.setPermissions(new LinkedHashSet<>(permissions));
        Long newRoleId = create(request);
        audit.recordRequired("role_change", "info", "role.clone", "role:" + newRoleId,
            Map.of("sourceRoleId", roleId), Map.of("roleCode", newCode), Map.of());
        return newRoleId;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void batchChangeStatus(List<Long> roleIds, String status) {
        for (Long roleId : roleIds) {
            RoleState role = requireRole(roleId);
            if ("kma-admin".equals(role.roleCode()) && "disabled".equals(status)) {
                throw new KmaException(409, "系统管理员角色不可停用");
            }
            jdbc.update("UPDATE kma_role SET status=?,update_time=now() WHERE role_id=?", status, roleId);
            invalidateRoleMembers(roleId);
            audit.recordRequired("role_change", "warning", "role.status.update", "role:" + roleId,
                Map.of("roleCode", role.roleCode()), Map.of("status", status), Map.of());
        }
        if (administrationGuard != null) administrationGuard.assertOperationalAdmins();
    }

    public List<Map<String, Object>> listRoleUsers(Long roleId) {
        requireRole(roleId);
        return jdbc.queryForList("""
            SELECT u.user_id,u.username,u.display_name,u.identity_provider,u.status
            FROM kma_user u
            JOIN kma_user_role ur ON ur.user_id=u.user_id
            WHERE ur.role_id=? ORDER BY u.username
            """, roleId);
    }

    private Set<String> normalizePermissions(Set<String> requested) {
        Set<String> selected = requested == null ? new LinkedHashSet<>() : new LinkedHashSet<>(requested);
        if (selected.contains("kma:admin")) throw new KmaException(403, "kma:admin 不能通过角色编辑分配");
        if (selected.isEmpty()) return selected;
        List<PermissionSelection> found = jdbc.query("""
            SELECT permission_code,parent_code,enabled,assignable
            FROM kma_permission WHERE permission_code = ANY (string_to_array(?, ','))
            """, (rs, row) -> new PermissionSelection(rs.getString("permission_code"), rs.getString("parent_code"),
            rs.getBoolean("enabled"), rs.getBoolean("assignable")),
            String.join(",", selected));
        if (found.size() != selected.size()) throw new KmaException(400, "包含不存在的权限编码");
        for (PermissionSelection permission : found) {
            if (!permission.enabled() || !permission.assignable()) throw new KmaException(400, "权限不可分配: " + permission.code());
            if (permission.parent() != null) selected.add(permission.parent());
        }
        return selected;
    }

    private void replacePermissions(Long roleId, Set<String> permissions) {
        jdbc.update("DELETE FROM kma_role_permission WHERE role_id=?", roleId);
        for (String permission : permissions) {
            jdbc.update("INSERT INTO kma_role_permission(role_id,permission_code) VALUES (?,?)",
                roleId, permission);
        }
    }

    private void invalidateRoleMembers(Long roleId) {
        jdbc.update("""
            UPDATE kma_user SET auth_version=auth_version+1,update_time=now()
            WHERE user_id IN (
                SELECT user_id FROM kma_user_role WHERE role_id=?
            )
            """, roleId);
    }

    private String permissionModule(String code) {
        if (code == null) return "系统管理";
        if (code.startsWith("user:") || code.startsWith("role:") || code.startsWith("org:") || code.startsWith("permission:")) return "组织权限";
        if (code.startsWith("content:") || code.startsWith("topic:") || code.startsWith("portal:")) return "内容治理";
        if (code.startsWith("space:") || code.startsWith("document:") || code.startsWith("dataset:") || code.startsWith("vector:")) return "知识技术";
        if (code.startsWith("retrieval:") || code.startsWith("qa:") || code.startsWith("eval:") || code.startsWith("rag:")) return "检索与 AI";
        if (code.startsWith("task:") || code.startsWith("audit:") || code.startsWith("storage:") || code.startsWith("model:") || code.startsWith("system:")) return "高级运维";
        return "系统管理";
    }

    private RoleState requireRole(Long roleId) {
        List<RoleState> roles = jdbc.query("""
            SELECT role_code,built_in FROM kma_role WHERE role_id=?
            """, (rs, row) -> new RoleState(rs.getString("role_code"), rs.getBoolean("built_in")), roleId);
        if (roles.isEmpty()) throw new KmaException(404, "角色不存在");
        return roles.get(0);
    }

    private record RoleState(String roleCode, boolean builtIn) {}
    private record PermissionSelection(String code, String parent, boolean enabled, boolean assignable) {}
    private record PermissionNodeRow(String code, String name, String parent, String type,
                                     String description, int sortOrder) {}
}
