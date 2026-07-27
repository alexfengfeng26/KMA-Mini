package com.kma.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.result.ApiResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Rejects local access tokens immediately after identity, role or organization grants change. */
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class AuthorizationVersionFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SecurityAuditService audit;

    public AuthorizationVersionFilter(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                                      ObjectMapper objectMapper) {
        this(jdbc, objectMapper, null);
    }

    @Autowired
    public AuthorizationVersionFilter(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                                      ObjectMapper objectMapper,
                                      SecurityAuditService audit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        if (principal == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!"local".equals(principal.getTokenSource())) {
            reject(response, 401, "AUTHORIZATION_STALE");
            return;
        }
        if (principal.getUserId() == null || principal.getAuthorizationVersion() == null) {
            reject(response, 401, "AUTHORIZATION_STALE");
            return;
        }
        List<AccountState> states = jdbc.query("""
            SELECT auth_version,status AS user_status
            FROM kma_user
            WHERE user_id=?
            """, (rs, row) -> new AccountState(rs.getLong("auth_version"),
            rs.getString("user_status")), principal.getUserId());
        if (states.size() != 1 || !"active".equalsIgnoreCase(states.get(0).userStatus())) {
            reject(response, 401, "ACCOUNT_DISABLED");
            return;
        }
        if (states.get(0).version() != principal.getAuthorizationVersion()) {
            reject(response, 401, "AUTHORIZATION_STALE");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), ApiResult.fail(status, code));
    }

    private record AccountState(long version, String userStatus) {}
}
