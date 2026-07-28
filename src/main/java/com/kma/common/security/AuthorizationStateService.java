package com.kma.common.security;

import com.kma.common.exception.KmaException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rechecks the current authorization epoch outside the HTTP filter chain.
 *
 * <p>SSE work continues on an executor after the original request filter has
 * completed.  This service gives those long-running operations the same
 * revocation semantics as a normal protected request.</p>
 */
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class AuthorizationStateService {
    private final JdbcTemplate jdbc;

    public AuthorizationStateService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void assertCurrent(KmaPrincipal principal) {
        if (!isCurrent(principal)) throw new KmaException(401, "AUTHORIZATION_REVOKED");
    }

    public boolean isCurrent(KmaPrincipal principal) {
        if (principal == null || !"local".equals(principal.getTokenSource())
            || principal.getUserId() == null || principal.getAuthorizationVersion() == null) {
            return false;
        }
        List<AccountState> rows = jdbc.query("""
            SELECT auth_version,status FROM kma_user WHERE user_id=?
            """, (rs, row) -> new AccountState(rs.getLong("auth_version"), rs.getString("status")),
            principal.getUserId());
        return rows.size() == 1 && "active".equalsIgnoreCase(rows.getFirst().status())
            && rows.getFirst().version() == principal.getAuthorizationVersion();
    }

    private record AccountState(long version, String status) {}
}
