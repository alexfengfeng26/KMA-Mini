package com.kma.common.security;

import com.kma.common.exception.KmaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KmaLocalAuthServiceReplayTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtEncoder jwtEncoder;
    @Mock private SecurityAuditService audit;

    @Test
    @SuppressWarnings("unchecked")
    void reusedRotatedRefreshTokenRevokesFamilyAndInvalidatesAuthorization() throws Exception {
        UUID tokenId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), anyString()))
            .thenAnswer(invocation -> {
                RowMapper<Object> mapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("token_id", UUID.class)).thenReturn(tokenId);
                when(rs.getLong("user_id")).thenReturn(7L);
                when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
                when(rs.getObject("family_id", UUID.class)).thenReturn(familyId);
                when(rs.getTimestamp("revoked_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
                when(rs.getObject("replaced_by", UUID.class)).thenReturn(replacementId);
                return List.of(mapper.mapRow(rs, 0));
            });
        KmaLocalAuthService service = new KmaLocalAuthService(jdbc, passwordEncoder, jwtEncoder,
            new KmaSecurityProperties(), audit);

        assertThatThrownBy(() -> service.refresh("reused-token"))
            .isInstanceOf(KmaException.class).hasMessage("REFRESH_TOKEN_REUSED");

        verify(jdbc).update(anyString(), eq(familyId));
        verify(jdbc).update(anyString(), eq(7L));
        verify(audit).recordRequired(eq("token_reuse"), eq("critical"), eq("auth.refresh.reuse"),
            eq("user:7"), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.anyMap());
    }

}
