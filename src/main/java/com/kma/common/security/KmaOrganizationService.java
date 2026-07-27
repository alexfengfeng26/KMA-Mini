package com.kma.common.security;

import com.kma.common.exception.KmaException;
import com.kma.common.result.PageResult;
import com.kma.common.security.dto.OrganizationCreateRequest;
import com.kma.common.security.dto.OrganizationMoveRequest;
import com.kma.common.security.dto.OrganizationNode;
import com.kma.common.security.dto.OrganizationUpdateRequest;
import com.kma.common.security.dto.UserOrganizationsRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class KmaOrganizationService {
    private final JdbcTemplate jdbc;
    private final SecurityAuditService audit;
    private final SpaceAdministrationGuard administrationGuard;

    public KmaOrganizationService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                                  SecurityAuditService audit) {
        this(jdbc, audit, null);
    }

    @Autowired
    public KmaOrganizationService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                                  SecurityAuditService audit,
                                  SpaceAdministrationGuard administrationGuard) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.administrationGuard = administrationGuard;
    }

    public List<OrganizationNode> tree() {
        List<OrganizationNode> rows = jdbc.query("""
            SELECT o.org_id,o.org_code,o.name,o.parent_id,o.status,o.built_in,o.sort_order,
                   count(uo.user_id) AS member_count
            FROM kma_org o LEFT JOIN kma_user_org uo ON uo.org_id=o.org_id
            GROUP BY o.org_id
            ORDER BY o.sort_order,o.org_code
            """, (rs, row) -> new OrganizationNode(rs.getLong("org_id"), rs.getString("org_code"),
            rs.getString("name"), (Long) rs.getObject("parent_id"), rs.getString("status"),
            rs.getBoolean("built_in"), rs.getInt("sort_order"), rs.getLong("member_count")));
        Map<Long, OrganizationNode> byId = new LinkedHashMap<>();
        rows.forEach(node -> byId.put(node.orgId(), node));
        List<OrganizationNode> roots = new ArrayList<>();
        for (OrganizationNode node : rows) {
            OrganizationNode parent = byId.get(node.parentId());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }

    public List<Map<String, Object>> members(Long orgId) {
        requireOrg(orgId);
        return jdbc.queryForList("""
            SELECT u.user_id,u.username,u.display_name,u.identity_provider,u.status,uo.primary_org
            FROM kma_user_org uo JOIN kma_user u
              ON u.user_id=uo.user_id
            WHERE uo.org_id=? ORDER BY u.username
            """, orgId);
    }

    public PageResult<Map<String, Object>> memberPage(Long orgId, int pageNum, int pageSize, String keyword,
                                                      String sortBy, String sortOrder) {
        requireOrg(orgId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String pattern = "%" + normalizedKeyword + "%";
        String orderColumn = switch (sortBy) {
            case "displayName" -> "u.display_name";
            case "status" -> "u.status";
            default -> "u.username";
        };
        String direction = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM kma_user_org uo JOIN kma_user u
              ON u.user_id=uo.user_id
            WHERE uo.org_id=?
              AND (?='' OR u.username ILIKE ? OR COALESCE(u.display_name,'') ILIKE ?)
            """, Long.class, orgId, normalizedKeyword, pattern, pattern);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT u.user_id,u.username,u.display_name,u.identity_provider,u.status,uo.primary_org
            FROM kma_user_org uo JOIN kma_user u
              ON u.user_id=uo.user_id
            WHERE uo.org_id=?
              AND (?='' OR u.username ILIKE ? OR COALESCE(u.display_name,'') ILIKE ?)
            ORDER BY %s %s, u.user_id LIMIT ? OFFSET ?
            """.formatted(orderColumn, direction), orgId, normalizedKeyword, pattern, pattern,
            pageSize, (pageNum - 1) * pageSize);
        return new PageResult<>(rows, total == null ? 0 : total, pageNum, pageSize);
    }

    public List<Map<String, Object>> userOrganizations(Long userId) {
        requireUser(userId);
        return jdbc.queryForList("""
            SELECT o.org_id,o.org_code,o.name,uo.primary_org
            FROM kma_user_org uo JOIN kma_org o
              ON o.org_id=uo.org_id
            WHERE uo.user_id=? ORDER BY uo.primary_org DESC,o.sort_order,o.org_code
            """, userId);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public Long create(OrganizationCreateRequest request) {
        requireOrg(request.parentId());
        try {
            Long id = jdbc.queryForObject("""
                INSERT INTO kma_org(org_code,name,parent_id,status,built_in,sort_order)
                VALUES (?,?,?,'active',FALSE,?) RETURNING org_id
                """, Long.class, request.orgCode(), request.name(), request.parentId(),
                request.sortOrder() == null ? 0 : request.sortOrder());
            rebuildClosure();
            audit.recordRequired("organization_change", "info", "org.create", "org:" + id, Map.of(),
                Map.of("orgCode", request.orgCode(), "parentId", request.parentId()), Map.of());
            return id;
        } catch (DataIntegrityViolationException ex) {
            throw new KmaException(409, "组织编码已存在或父组织无效");
        }
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void update(Long orgId, OrganizationUpdateRequest request) {
        OrgState org = requireOrg(orgId);
        if ("disabled".equals(request.status()) && administrationGuard != null) {
            administrationGuard.assertPrincipalCanBeDisabled("org", org.code());
        }
        jdbc.update("""
            UPDATE kma_org SET name=?,status=?,sort_order=?,update_time=now()
            WHERE org_id=?
            """, request.name(), request.status() == null ? "active" : request.status(),
            request.sortOrder() == null ? 0 : request.sortOrder(), orgId);
        invalidateMembers(Set.of(orgId), true);
        if (administrationGuard != null) administrationGuard.assertOperationalAdmins();
        audit.recordRequired("organization_change", "warning", "org.update", "org:" + orgId,
            Map.of("orgCode", org.code()),
            Map.of("orgCode", org.code(), "status", request.status() == null ? "active" : request.status()),
            Map.of("authorizationVersionInvalidated", true));
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void move(Long orgId, OrganizationMoveRequest request) {
        OrgState org = requireOrg(orgId);
        requireOrg(request.parentId());
        if (org.builtIn()) throw new KmaException(409, "根组织不可移动");
        if (orgId.equals(request.parentId())) throw new KmaException(409, "组织不能移动到自身下级");
        Integer cycle = jdbc.queryForObject("""
            SELECT count(*) FROM kma_org_closure
            WHERE ancestor_id=? AND descendant_id=?
            """, Integer.class, orgId, request.parentId());
        if (cycle != null && cycle > 0) throw new KmaException(409, "组织不能移动到自身下级");
        Set<Long> subtree = descendantIds(orgId);
        jdbc.update("UPDATE kma_org SET parent_id=?,update_time=now() WHERE org_id=?",
            request.parentId(), orgId);
        rebuildClosure();
        invalidateMembers(subtree, false);
        if (administrationGuard != null) administrationGuard.assertOperationalAdmins();
        audit.recordRequired("organization_change", "warning", "org.move", "org:" + orgId,
            Map.of("orgCode", org.code()), Map.of("orgCode", org.code(), "parentId", request.parentId()), Map.of());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void delete(Long orgId) {
        OrgState org = requireOrg(orgId);
        if (org.builtIn()) throw new KmaException(409, "根组织不可删除");
        Integer children = jdbc.queryForObject("SELECT count(*) FROM kma_org WHERE parent_id=?",
            Integer.class, orgId);
        Integer members = jdbc.queryForObject("SELECT count(*) FROM kma_user_org WHERE org_id=?",
            Integer.class, orgId);
        Integer acls = jdbc.queryForObject("""
            SELECT count(*) FROM knowledge_space_acl
            WHERE principal_type='org' AND principal_value=?
            """, Integer.class, org.code());
        if ((children != null && children > 0) || (members != null && members > 0) || (acls != null && acls > 0)) {
            throw new KmaException(409, "组织仍有下级、成员或空间授权，不能删除");
        }
        jdbc.update("DELETE FROM kma_org WHERE org_id=?", orgId);
        rebuildClosure();
        audit.recordRequired("organization_change", "warning", "org.delete", "org:" + orgId,
            Map.of("orgCode", org.code()), Map.of(), Map.of());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void setUserOrganizations(Long userId, UserOrganizationsRequest request) {
        requireUser(userId);
        Set<Long> ids = new LinkedHashSet<>(request.organizationIds());
        Long primaryOrganizationId = request.primaryOrganizationId();
        if (ids.isEmpty()) {
            Long rootId = jdbc.queryForObject("""
                SELECT org_id FROM kma_org
                WHERE org_code='root' AND status='active'
                """, Long.class);
            if (rootId == null) throw new KmaException(409, "根组织不存在或不可用");
            ids.add(rootId);
            primaryOrganizationId = rootId;
        }
        if (primaryOrganizationId == null) {
            throw new KmaException(400, "必须从所属组织中指定一个主组织");
        }
        if (!ids.contains(primaryOrganizationId)) {
            throw new KmaException(400, "主组织必须包含在用户组织列表中");
        }
        if (!ids.isEmpty()) {
            Integer valid = jdbc.queryForObject("""
                SELECT count(*) FROM kma_org WHERE status='active'
                  AND org_id = ANY (string_to_array(?, ',')::bigint[])
                """, Integer.class, ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
            if (valid == null || valid != ids.size()) throw new KmaException(400, "包含不存在或已停用的组织");
        }
        jdbc.update("DELETE FROM kma_user_org WHERE user_id=?", userId);
        for (Long orgId : ids) {
            jdbc.update("INSERT INTO kma_user_org(user_id,org_id,primary_org) VALUES (?,?,?)",
                userId, orgId, orgId.equals(primaryOrganizationId));
        }
        jdbc.update("UPDATE kma_user SET auth_version=auth_version+1,update_time=now() WHERE user_id=?",
            userId);
        if (administrationGuard != null) administrationGuard.assertOperationalAdmins();
        audit.recordRequired("organization_membership_change", "warning", "org.members.update", "user:" + userId,
            Map.of(), Map.of("organizationIds", ids), Map.of("authorizationVersionInvalidated", true));
    }

    public Set<String> ancestorCodes(Collection<String> directCodes) {
        if (directCodes == null || directCodes.isEmpty()) return Set.of();
        List<String> rows = jdbc.queryForList("""
            SELECT DISTINCT ancestor.org_code
            FROM kma_org direct
            JOIN kma_org_closure c ON c.descendant_id=direct.org_id
            JOIN kma_org ancestor ON ancestor.org_id=c.ancestor_id
            WHERE direct.status='active' AND ancestor.status='active'
              AND direct.org_code = ANY (string_to_array(?, ','))
            """, String.class, String.join(",", directCodes));
        return new LinkedHashSet<>(rows);
    }

    private void rebuildClosure() {
        jdbc.update("DELETE FROM kma_org_closure");
        jdbc.update("""
            WITH RECURSIVE paths(ancestor_id,descendant_id,depth) AS (
                SELECT org_id,org_id,0 FROM kma_org
                UNION ALL
                SELECT p.ancestor_id,o.org_id,p.depth+1
                FROM paths p JOIN kma_org o ON o.parent_id=p.descendant_id
            )
            INSERT INTO kma_org_closure(ancestor_id,descendant_id,depth)
            SELECT ancestor_id,descendant_id,depth FROM paths
            """);
    }

    private Set<Long> descendantIds(Long orgId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
            SELECT descendant_id FROM kma_org_closure WHERE ancestor_id=?
            """, Long.class, orgId));
    }

    private void invalidateMembers(Collection<Long> orgIds, boolean includeDescendants) {
        Set<Long> targets = new LinkedHashSet<>();
        for (Long orgId : orgIds) targets.addAll(includeDescendants ? descendantIds(orgId) : Set.of(orgId));
        if (targets.isEmpty()) return;
        jdbc.update("""
            UPDATE kma_user SET auth_version=auth_version+1,update_time=now()
            WHERE user_id IN (
                SELECT DISTINCT user_id FROM kma_user_org
                WHERE org_id = ANY (string_to_array(?, ',')::bigint[])
            )
            """, targets.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private OrgState requireOrg(Long orgId) {
        List<OrgState> rows = jdbc.query("SELECT org_code,built_in FROM kma_org WHERE org_id=?",
            (rs, row) -> new OrgState(rs.getString("org_code"), rs.getBoolean("built_in")), orgId);
        if (rows.isEmpty()) throw new KmaException(404, "组织不存在");
        return rows.get(0);
    }

    private void requireUser(Long userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM kma_user WHERE user_id=?",
            Integer.class, userId);
        if (count == null || count == 0) throw new KmaException(404, "用户不存在");
    }

    private record OrgState(String code, boolean builtIn) {}
}
