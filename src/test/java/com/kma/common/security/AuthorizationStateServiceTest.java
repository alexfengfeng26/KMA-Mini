package com.kma.common.security;

import com.kma.common.exception.KmaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationStateServiceTest {
    @Mock private JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void acceptsOnlyCurrentActiveLocalIdentity() throws Exception {
        KmaPrincipal principal = principal(7L, 3L);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L)))
            .thenAnswer(invocation -> {
                RowMapper<Object> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(row(3L, "active"), 0));
            });

        AuthorizationStateService service = new AuthorizationStateService(jdbc);
        assertThat(service.isCurrent(principal)).isTrue();
        service.assertCurrent(principal);

        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(7L)))
            .thenAnswer(invocation -> {
                RowMapper<Object> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(row(4L, "active"), 0));
            });
        assertThat(service.isCurrent(principal)).isFalse();
        assertThatThrownBy(() -> service.assertCurrent(principal))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(401);
    }

    private KmaPrincipal principal(long userId, long version) {
        KmaPrincipal principal = new KmaPrincipal();
        principal.setTokenSource("local");
        principal.setUserId(userId);
        principal.setAuthorizationVersion(version);
        return principal;
    }

    private ResultSet row(long version, String status) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("auth_version")).thenReturn(version);
        when(rs.getString("status")).thenReturn(status);
        return rs;
    }
}
