package com.kma.common.security;

import com.kma.common.exception.KmaException;
import com.kma.common.security.dto.UserCreateRequest;
import com.kma.common.security.dto.RoleUpsertRequest;
import com.kma.common.security.dto.UserRolesRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KmaUserAdminServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecurityAuditService audit;

    private KmaUserAdminService service;
    private KmaPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new KmaPrincipal();
        principal.setUserId(1L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        service = new KmaUserAdminService(jdbc, passwordEncoder, audit);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsAllUsers() {
        List<Map<String, Object>> rows = List.of(Map.of("user_id", 7L));
        when(jdbc.queryForList(anyString())).thenReturn(rows);

        assertThat(service.list()).isSameAs(rows);
        verify(jdbc).queryForList(anyString());
    }

    @Test
    void createsLocalUserAndAssignsValidatedRoles() {
        UserCreateRequest request = request(List.of("editor"));
        when(jdbc.queryForList(anyString(), eq("editor")))
            .thenReturn(List.of(Map.of("role_code", "editor", "application_admin_role", false)));
        when(passwordEncoder.encode("initial-password")).thenReturn("argon-hash");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("alice"),
            eq("Alice"), eq("argon-hash"))).thenReturn(7L);
        lenient().when(jdbc.update(anyString(), eq(7L), eq("editor"))).thenReturn(1);
        when(jdbc.update(anyString(), eq(7L))).thenReturn(1);

        assertThat(service.create(request)).isEqualTo(7L);
        verify(jdbc).update(anyString(), eq(7L));
    }

    @Test
    void rejectsDuplicateUserWithoutAssigningRoles() {
        UserCreateRequest request = request(List.of("editor"));
        when(jdbc.queryForList(anyString(), eq("editor")))
            .thenReturn(List.of(Map.of("role_code", "editor", "application_admin_role", false)));
        when(passwordEncoder.encode(anyString())).thenReturn("argon-hash");
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(KmaException.class)
            .extracting("code").isEqualTo(409);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    void rejectsUnknownRoleSoTransactionCanRollBackUserCreation() {
        UserCreateRequest request = request(List.of("missing"));

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(KmaException.class)
            .hasMessageContaining("不存在或已停用")
            .extracting("code").isEqualTo(400);
    }

    @Test
    void statusAndPasswordChangesFailClosedWhenUserDoesNotExist() {
        when(jdbc.update(anyString(), eq("disabled"), eq(99L))).thenReturn(0);
        assertThatThrownBy(() -> service.changeStatus(99L, "disabled"))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(404);

        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");
        when(jdbc.update(anyString(), eq("new-hash"), eq(99L))).thenReturn(0);
        assertThatThrownBy(() -> service.resetPassword(99L, "new-password-123"))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(404);
    }

    @Test
    void resettingPasswordRevokesExistingRefreshTokens() {
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");
        when(jdbc.update(anyString(), eq("new-hash"), eq(7L))).thenReturn(1);

        service.resetPassword(7L, "new-password-123");

        verify(jdbc).update(anyString(), eq(7L));
    }

    @Test
    void disablesNonAdminAndRevokesTokensImmediately() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(0);
        when(jdbc.update(anyString(), eq("disabled"), eq(7L))).thenReturn(1);

        service.changeStatus(7L, "disabled");

        verify(jdbc).update(anyString(), eq(7L));
        verify(audit).recordRequired(eq("identity_change"), eq("warning"), eq("user.status.update"), eq("user:7"),
            any(), any(), any());
    }

    @Test
    void protectsTheLastApplicationAdministrator() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L)))
            .thenReturn(1, 0);

        assertThatThrownBy(() -> service.changeStatus(1L, "disabled"))
            .isInstanceOf(KmaException.class).hasMessageContaining("LAST_ADMIN_REQUIRED");
    }

    @Test
    void updatesRolesAndAuthorizationVersion() {
        UserRolesRequest request = new UserRolesRequest();
        request.setRoles(Set.of("editor"));
        when(jdbc.queryForList(anyString(), eq("editor")))
            .thenReturn(List.of(Map.of("role_code", "editor", "application_admin_role", false)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L)))
            .thenReturn(1, 0);
        lenient().when(jdbc.update(anyString(), eq(7L), eq("editor")))
            .thenReturn(1);

        service.updateRoles(7L, request);

        verify(jdbc, times(2)).update(anyString(), eq(7L));
        verify(audit).recordRequired(eq("identity_change"), eq("warning"), eq("user.roles.update"), eq("user:7"),
            any(), any(), any());
    }

    @Test
    void onlyApplicationAdminCanAssignApplicationAdminRole() {
        when(jdbc.queryForList(anyString(), eq("kma-admin")))
            .thenReturn(List.of(Map.of("role_code", "kma-admin", "application_admin_role", true)));
        assertThatThrownBy(() -> service.create(request(List.of("kma-admin"))))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(403);

        principal.setPermissions(Set.of("kma:admin"));
        when(passwordEncoder.encode("initial-password")).thenReturn("argon-hash");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("alice"),
            eq("Alice"), eq("argon-hash"))).thenReturn(8L);
        when(jdbc.update(anyString(), eq(8L), eq("kma-admin")))
            .thenReturn(1);
        when(jdbc.update(anyString(), eq(8L))).thenReturn(1);
        assertThat(service.create(request(List.of("kma-admin")))).isEqualTo(8L);
    }

    @Test
    void reactivatingUserRestoresRootOrganizationWhenUnassigned() {
        when(jdbc.update(anyString(), eq("active"), eq(7L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(7L), eq(7L)))
            .thenReturn(1);

        service.changeStatus(7L, "active");

        verify(audit).recordRequired(eq("identity_change"), eq("warning"), eq("user.organization.default"),
            eq("user:7"), any(), any(), any());
    }

    @Test
    void revokesTokensAndSupportsCompatibilityRoleApis() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        service.revokeTokens(7L);
        verify(audit).recordRequired(eq("identity_change"), eq("warning"), eq("user.tokens.revoke"), eq("user:7"),
            any(), any(), any());

        List<Map<String, Object>> roles = List.of(Map.of("role_code", "reader"));
        List<Map<String, Object>> permissions = List.of(Map.of("permission_code", "space:read"));
        when(jdbc.queryForList(anyString())).thenAnswer(invocation ->
            invocation.<String>getArgument(0).contains("FROM kma_role") ? roles : permissions);
        assertThat(service.listRoles()).isSameAs(roles);
        assertThat(service.listPermissions()).isSameAs(permissions);

        RoleUpsertRequest role = new RoleUpsertRequest();
        role.setRoleCode("reader");
        role.setName("Reader");
        role.setPermissions(Set.of("space:read"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("space:read"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("reader"), eq("Reader")))
            .thenReturn(11L);
        lenient().when(jdbc.update(anyString(), eq(11L), eq("space:read"))).thenReturn(1);
        assertThat(service.upsertRole(role)).isEqualTo(11L);
    }

    private UserCreateRequest request(List<String> roles) {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setDisplayName("Alice");
        request.setInitialPassword("initial-password");
        request.setRoles(roles);
        return request;
    }
}
