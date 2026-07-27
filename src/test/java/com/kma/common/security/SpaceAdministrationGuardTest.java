package com.kma.common.security;

import com.kma.common.exception.KmaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceAdministrationGuardTest {
    @Mock private JdbcTemplate jdbc;

    @Test
    void blocksLastActiveAclAndAllowsAnotherActiveAdministrator() {
        SpaceAdministrationGuard guard = new SpaceAdministrationGuard(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(9L), eq(3L)))
            .thenReturn(0, 1);

        assertThatThrownBy(() -> guard.assertAclRemovalAllowed(9L, 3L))
            .isInstanceOf(KmaException.class).hasMessage("LAST_EFFECTIVE_SPACE_ADMIN_REQUIRED");
        assertThatCode(() -> guard.assertAclRemovalAllowed(9L, 3L)).doesNotThrowAnyException();
    }

    @Test
    void reportsMissingPrincipalAsIneffective() {
        SpaceAdministrationGuard guard = new SpaceAdministrationGuard(jdbc);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
            eq("reader"))).thenReturn(List.of());

        SpaceAdministrationGuard.PrincipalState state = guard.principalState("role", "reader");

        assertThat(state).isNull();
    }
}
