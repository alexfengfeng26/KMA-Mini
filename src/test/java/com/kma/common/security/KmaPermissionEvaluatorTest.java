package com.kma.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KmaPermissionEvaluatorTest {
    private final KmaPermissionEvaluator evaluator = new KmaPermissionEvaluator();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stablePermissionsAuthorizeLegacyControllerExpressions() {
        authenticate(Set.of("space:manage", "document:ingest", "qa:use", "audit:read"));

        assertThat(evaluator.hasPermi("knowledge:space:list")).isTrue();
        assertThat(evaluator.hasPermi("knowledge:dataset:edit")).isTrue();
        assertThat(evaluator.hasPermi("knowledge:ingest:add")).isTrue();
        assertThat(evaluator.hasPermi("knowledge:qa:query")).isTrue();
        assertThat(evaluator.hasPermi("knowledge:metrics:query")).isTrue();
        assertThat(evaluator.hasPermi("model:manage")).isFalse();
    }

    @Test
    void adminPermissionAuthorizesEveryOperation() {
        authenticate(Set.of("kma:admin"));
        assertThat(evaluator.hasPermi("model:manage")).isTrue();
    }

    private void authenticate(Set<String> permissions) {
        KmaPrincipal principal = new KmaPrincipal();
        principal.setUsername("tester");
        principal.setPermissions(permissions);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
