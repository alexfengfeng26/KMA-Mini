package com.kma.common.security;

import com.kma.common.security.dto.RoleUpsertRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminControllerAuthorizationTest {
    @Mock private KmaUserAdminService users;
    @Mock private KmaRoleAdminService roles;

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void createPermissionCannotUpdateExistingRole() {
        authenticate(Set.of("role:create"));
        when(roles.list()).thenReturn(List.of(Map.of("roleId", 9L, "roleCode", "editor")));

        assertThatThrownBy(() -> new UserAdminController(users, roles).upsertRole(request("editor")))
            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("role:update");
    }

    @Test
    void updatePermissionCannotCreateMissingRole() {
        authenticate(Set.of("role:update"));
        when(roles.list()).thenReturn(List.of());

        assertThatThrownBy(() -> new UserAdminController(users, roles).upsertRole(request("editor")))
            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("role:create");
    }

    private RoleUpsertRequest request(String code) {
        RoleUpsertRequest request = new RoleUpsertRequest();
        request.setRoleCode(code); request.setName("Editor"); request.setPermissions(Set.of("space:read"));
        return request;
    }

    private void authenticate(Set<String> permissions) {
        KmaPrincipal principal = new KmaPrincipal();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
