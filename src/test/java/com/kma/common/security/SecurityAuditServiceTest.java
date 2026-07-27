package com.kma.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {
    @Mock private JdbcTemplate jdbc;

    @Test
    void requiredAuditFailsClosedWhileBestEffortAuditDegrades() {
        doThrow(new DataAccessResourceFailureException("audit unavailable"))
            .when(jdbc).update(anyString(), any(Object[].class));
        SecurityAuditService service = new SecurityAuditService(jdbc, new ObjectMapper());

        assertThatThrownBy(() -> service.recordRequired("role_change", "warning", "role.update", "role:7",
            Map.of("name", "before"), Map.of("name", "after"), Map.of()))
            .isInstanceOf(KmaException.class)
            .extracting("code").isEqualTo(409);

        assertThatCode(() -> service.recordBestEffort("login_failure", "warning", "auth.login.failed",
            "application", null, List.of(), Map.of())).doesNotThrowAnyException();
    }
}
