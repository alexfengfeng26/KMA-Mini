package com.kma.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.ResultSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationVersionFilterTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private FilterChain chain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestContinuesWithoutDatabaseLookup() throws Exception {
        AuthorizationVersionFilter filter = new AuthorizationVersionFilter(jdbc, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void localTokenWithoutVersionIsRejectedAsStale() throws Exception {
        KmaPrincipal principal = principal("local", 7L, null);
        authenticate(principal);
        AuthorizationVersionFilter filter = new AuthorizationVersionFilter(jdbc, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTHORIZATION_STALE");
    }

    @Test
    void developmentIdentityIsRejectedAsStale() throws Exception {
        KmaPrincipal principal = principal("dev", 7L, null);
        principal.setPermissions(Set.of("kma:admin"));
        authenticate(principal);
        AuthorizationVersionFilter filter = new AuthorizationVersionFilter(jdbc, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTHORIZATION_STALE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsCurrentLocalTokenAndRejectsStaleOrDisabledAccount() throws Exception {
        KmaPrincipal principal = principal("local", 7L, 3L);
        authenticate(principal);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
            eq(7L))).thenAnswer(invocation -> {
                RowMapper<Object> mapper = invocation.getArgument(1);
            ResultSet rs = accountRow(3L, "active");
                return List.of(mapper.mapRow(rs, 0));
            });
        AuthorizationVersionFilter filter = new AuthorizationVersionFilter(jdbc, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);

        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
            eq(7L))).thenAnswer(invocation -> {
                RowMapper<Object> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(accountRow(4L, "active"), 0));
            });
        MockHttpServletResponse staleResponse = new MockHttpServletResponse();
        filter.doFilterInternal(new MockHttpServletRequest(), staleResponse, chain);
        assertThat(staleResponse.getStatus()).isEqualTo(401);
        assertThat(staleResponse.getContentAsString()).contains("AUTHORIZATION_STALE");
        assertThat(staleResponse.getHeader("Cache-Control")).isEqualTo("no-store");

        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
            eq(7L))).thenReturn(List.of());
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilterInternal(new MockHttpServletRequest(), missingResponse, chain);
        assertThat(missingResponse.getStatus()).isEqualTo(401);
        assertThat(missingResponse.getContentAsString()).contains("ACCOUNT_DISABLED");
    }

    private KmaPrincipal principal(String source, Long userId, Long version) {
        KmaPrincipal principal = new KmaPrincipal();
        principal.setUserId(userId);
        principal.setAuthorizationVersion(version);
        principal.setTokenSource(source);
        principal.setPermissions(Set.of());
        return principal;
    }

    private void authenticate(KmaPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ResultSet accountRow(Long version, String userStatus) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("auth_version")).thenReturn(version);
        when(rs.getString("user_status")).thenReturn(userStatus);
        return rs;
    }
}
