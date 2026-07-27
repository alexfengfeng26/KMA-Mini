package com.kma.knowledge.service;

import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.SpaceAclView;
import com.kma.knowledge.entity.KnowledgeSpaceAcl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class AclPrincipalValidator {
    private final JdbcTemplate jdbc;

    public AclPrincipalValidator(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validate(String type, String value) {
        if (type == null || value == null) throw new KmaException(400, "ACL 主体不能为空");
        Integer count = switch (type) {
            case "user" -> jdbc.queryForObject("""
                SELECT count(*) FROM kma_user WHERE user_id::varchar=? AND status='active'
                """, Integer.class, value);
            case "role" -> jdbc.queryForObject("""
                SELECT count(*) FROM kma_role WHERE role_code=? AND status='active'
                """, Integer.class, value);
            case "org" -> jdbc.queryForObject("""
                SELECT count(*) FROM kma_org WHERE org_code=? AND status='active'
                """, Integer.class, value);
            default -> throw new KmaException(400, "不支持的 ACL 主体类型");
        };
        if (count == null || count == 0) throw new KmaException(400, "ACL 主体不存在或已停用");
    }

    public List<Map<String, Object>> list(String type, String keyword) {
        String search = keyword == null ? "" : keyword.trim();
        return switch (type) {
            case "user" -> jdbc.query("""
                SELECT user_id::varchar value,COALESCE(display_name,username) label,username secondary
                FROM kma_user WHERE status='active'
                  AND (?='' OR username ILIKE '%'||?||'%' OR display_name ILIKE '%'||?||'%')
                ORDER BY username LIMIT 100
                """, (rs, row) -> option("user", rs.getString("value"), rs.getString("label"), rs.getString("secondary")),
                search, search, search);
            case "role" -> jdbc.query("""
                SELECT role_code value,name label,role_code secondary FROM kma_role
                WHERE status='active' AND (?='' OR role_code ILIKE '%'||?||'%' OR name ILIKE '%'||?||'%')
                ORDER BY role_code LIMIT 100
                """, (rs, row) -> option("role", rs.getString("value"), rs.getString("label"), rs.getString("secondary")),
                search, search, search);
            case "org" -> jdbc.query("""
                SELECT org_code value,name label,org_code secondary FROM kma_org
                WHERE status='active' AND (?='' OR org_code ILIKE '%'||?||'%' OR name ILIKE '%'||?||'%')
                ORDER BY sort_order,org_code LIMIT 100
                """, (rs, row) -> option("org", rs.getString("value"), rs.getString("label"), rs.getString("secondary")),
                search, search, search);
            default -> throw new KmaException(400, "不支持的 ACL 主体类型");
        };
    }

    public SpaceAclView toView(KnowledgeSpaceAcl acl) {
        PrincipalStatus status = principalStatus(acl.getPrincipalType(), acl.getPrincipalValue());
        return new SpaceAclView(acl.getAclId(), acl.getSpaceId(), acl.getPrincipalType(), acl.getPrincipalValue(),
            displayName(acl.getPrincipalType(), acl.getPrincipalValue()), acl.getPermission(), acl.getCreateTime(),
            status.status(), status.effective(), status.reason());
    }

    private PrincipalStatus principalStatus(String type, String value) {
        List<String> states = switch (type) {
            case "user" -> jdbc.queryForList("SELECT status FROM kma_user WHERE user_id::varchar=?",
                String.class, value);
            case "role" -> jdbc.queryForList("SELECT status FROM kma_role WHERE role_code=?",
                String.class, value);
            case "org" -> jdbc.queryForList("SELECT status FROM kma_org WHERE org_code=?",
                String.class, value);
            default -> List.of();
        };
        if (states.isEmpty()) return new PrincipalStatus("missing", false, "PRINCIPAL_MISSING");
        boolean effective = "active".equalsIgnoreCase(states.get(0));
        return new PrincipalStatus(states.get(0), effective, effective ? null : "PRINCIPAL_INACTIVE");
    }

    private String displayName(String type, String value) {
        List<String> names = switch (type) {
            case "user" -> jdbc.queryForList("""
                SELECT COALESCE(display_name,username) FROM kma_user WHERE user_id::varchar=?
                """, String.class, value);
            case "role" -> jdbc.queryForList("SELECT name FROM kma_role WHERE role_code=?",
                String.class, value);
            case "org" -> jdbc.queryForList("SELECT name FROM kma_org WHERE org_code=?",
                String.class, value);
            default -> List.of();
        };
        return names.isEmpty() ? value : names.get(0);
    }

    private Map<String, Object> option(String type, String value, String label, String secondary) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type); item.put("value", value); item.put("label", label); item.put("secondary", secondary);
        return item;
    }

    private record PrincipalStatus(String status, boolean effective, String reason) {}
}
