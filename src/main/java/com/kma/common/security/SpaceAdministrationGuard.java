package com.kma.common.security;

import com.kma.common.exception.KmaException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpaceAdministrationGuard {
    private final JdbcTemplate jdbc;

    public SpaceAdministrationGuard(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void assertAclRemovalAllowed(Long spaceId, Long excludedAclId) {
        Integer active = jdbc.queryForObject("""
            SELECT count(*) FROM knowledge_space_acl a
            WHERE a.space_id=? AND a.permission='admin' AND a.acl_id<>?
              AND kma_acl_principal_is_active(a.principal_type,a.principal_value)
            """, Integer.class, spaceId, excludedAclId == null ? -1L : excludedAclId);
        if (active == null || active == 0) throw new KmaException(409, "LAST_EFFECTIVE_SPACE_ADMIN_REQUIRED");
    }

    public void assertPrincipalCanBeDisabled(String principalType, String principalValue) {
        Integer blocked = jdbc.queryForObject("""
            SELECT count(*) FROM knowledge_space_acl a
            WHERE a.principal_type=? AND a.principal_value=? AND a.permission='admin'
              AND NOT EXISTS (
                  SELECT 1 FROM knowledge_space_acl other
                  WHERE other.space_id=a.space_id AND other.permission='admin'
                    AND NOT (other.principal_type=a.principal_type AND other.principal_value=a.principal_value)
                    AND kma_acl_principal_is_active(other.principal_type,other.principal_value)
              )
            """, Integer.class, principalType, principalValue);
        if (blocked != null && blocked > 0) throw new KmaException(409, "LAST_EFFECTIVE_SPACE_ADMIN_REQUIRED");
    }

    public void assertOperationalAdmins() {
        Integer spacesWithoutAdmin = jdbc.queryForObject("""
            SELECT count(*) FROM knowledge_space s
            WHERE NOT EXISTS (
                SELECT 1 FROM knowledge_space_acl a
                WHERE a.space_id=s.space_id AND a.permission='admin'
                  AND kma_acl_principal_is_active(a.principal_type,a.principal_value)
            )
            """, Integer.class);
        if (spacesWithoutAdmin != null && spacesWithoutAdmin > 0) {
            throw new KmaException(409, "LAST_EFFECTIVE_SPACE_ADMIN_REQUIRED");
        }
        Integer applicationAdmins = jdbc.queryForObject("""
            SELECT count(DISTINCT u.user_id)
            FROM kma_user u
            JOIN kma_user_role ur ON ur.user_id=u.user_id
            JOIN kma_role r ON r.role_id=ur.role_id AND r.status='active'
            JOIN kma_role_permission rp ON rp.role_id=r.role_id
            WHERE u.status='active' AND rp.permission_code='kma:admin'
            """, Integer.class);
        if (applicationAdmins == null || applicationAdmins == 0) {
            throw new KmaException(409, "LAST_APPLICATION_ADMIN_REQUIRED");
        }
    }

    public void assertNotLastApplicationAdmin(Long userId) {
        Integer targetIsAdmin = jdbc.queryForObject("""
            SELECT count(*) FROM kma_user_role ur
            JOIN kma_role r ON r.role_id=ur.role_id AND r.status='active'
            JOIN kma_role_permission rp ON rp.role_id=r.role_id
            WHERE ur.user_id=? AND rp.permission_code='kma:admin'
            """, Integer.class, userId);
        if (targetIsAdmin == null || targetIsAdmin == 0) return;
        Integer alternatives = jdbc.queryForObject("""
            SELECT count(DISTINCT u.user_id)
            FROM kma_user u
            JOIN kma_user_role ur ON ur.user_id=u.user_id
            JOIN kma_role r ON r.role_id=ur.role_id AND r.status='active'
            JOIN kma_role_permission rp ON rp.role_id=r.role_id
            WHERE u.status='active' AND u.user_id<>? AND rp.permission_code='kma:admin'
            """, Integer.class, userId);
        if (alternatives == null || alternatives == 0) {
            throw new KmaException(409, "LAST_APPLICATION_ADMIN_REQUIRED");
        }
    }

    public PrincipalState principalState(String type, String value) {
        List<PrincipalState> rows = switch (type) {
            case "user" -> jdbc.query("""
                SELECT status,COALESCE(display_name,username) name FROM kma_user WHERE user_id::text=?
                """, (rs, row) -> state(rs.getString("status"), rs.getString("name")), value);
            case "role" -> jdbc.query("SELECT status,name FROM kma_role WHERE role_code=?",
                (rs, row) -> state(rs.getString("status"), rs.getString("name")), value);
            case "org" -> jdbc.query("SELECT status,name FROM kma_org WHERE org_code=?",
                (rs, row) -> state(rs.getString("status"), rs.getString("name")), value);
            default -> List.of();
        };
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private PrincipalState state(String status, String name) {
        return new PrincipalState(status, name);
    }

    public record PrincipalState(String status, String name) {}
}
