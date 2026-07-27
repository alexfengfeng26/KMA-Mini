package com.kma.common.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KmaPermissionCatalogTest {

    @Test
    void actionPermissionsAutomaticallyGrantTheirMenuEntry() {
        assertThat(KmaPermissionCatalog.expand(Set.of("space:acl:manage")))
            .containsExactlyInAnyOrder("space:acl:manage", "space:read");
        assertThat(KmaPermissionCatalog.expand(Set.of("evaluation:run")))
            .contains("evaluation:read");
    }

    @Test
    void legacyPermissionsRemainCompatibleForTwoReleaseMigrationWindow() {
        assertThat(KmaPermissionCatalog.expand(Set.of("space:manage")))
            .contains("space:read", "space:create", "space:delete", "embedding:activate");
        assertThat(KmaPermissionCatalog.has(Set.of("knowledge:space:auth"), "space:acl:manage")).isTrue();
        assertThat(KmaPermissionCatalog.has(Set.of("space:read"), "space:delete")).isFalse();
    }
}
