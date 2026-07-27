package com.kma.common.security;

import com.kma.common.exception.KmaException;
import com.kma.common.security.dto.PermissionNode;
import com.kma.common.security.dto.RoleUpsertRequest;
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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Array;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KmaRoleAdminServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private SecurityAuditService audit;
    private KmaRoleAdminService service;
    private KmaPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new KmaPrincipal();
        principal.setUserId(1L);
        principal.setPermissions(Set.of());
        principal.setRoles(Set.of("kma-admin"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        service = new KmaRoleAdminService(jdbc, audit);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsRolesAndBuildsPermissionTree() throws Exception {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
            .thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                RowMapper<Object> mapper = invocation.getArgument(1);
                if (!sql.contains("FROM kma_role r")) {
                    ResultSet parent = permissionRow("space:read", "空间", null, "menu", "application", 10);
                    ResultSet child = permissionRow("space:create", "创建", "space:read", "action", "application", 11);
                    return List.of(mapper.mapRow(parent, 0), mapper.mapRow(child, 1));
                }
                ResultSet rs = mock(ResultSet.class);
                Array permissions = mock(Array.class);
                when(permissions.getArray()).thenReturn(new String[]{"space:read"});
                when(rs.getLong("role_id")).thenReturn(7L);
                when(rs.getString("role_code")).thenReturn("reader");
                when(rs.getString("name")).thenReturn("Reader");
                when(rs.getString("description")).thenReturn("read only");
                when(rs.getString("status")).thenReturn("active");
                when(rs.getBoolean("built_in")).thenReturn(false);
                when(rs.getArray("permissions")).thenReturn(permissions);
                when(rs.getLong("user_count")).thenReturn(2L);
                when(rs.getBoolean("application_admin_role")).thenReturn(false);
                return List.of(mapper.mapRow(rs, 0));
            });

        List<Map<String, Object>> roles = service.list();
        List<PermissionNode> tree = service.permissionTree();

        assertThat(roles).singleElement().satisfies(role -> {
            assertThat(role.get("roleCode")).isEqualTo("reader");
            assertThat(role.get("assignable")).isEqualTo(true);
            assertThat(role.get("permissions")).isEqualTo(List.of("space:read"));
        });
        assertThat(tree).singleElement().satisfies(node -> {
            assertThat(node.permissionCode()).isEqualTo("space:read");
            assertThat(node.children()).extracting(PermissionNode::permissionCode).containsExactly("space:create");
        });
    }

    @Test
    void createsRoleAndAutomaticallyAddsParentPermission() throws Exception {
        RoleUpsertRequest request = request("editor", Set.of("document:ingest"));
        stubPermission("document:ingest", "document:read", "application", true, true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("editor"), eq("Editor"),
            eq("description"), eq("active"))).thenReturn(9L);

        assertThat(service.create(request)).isEqualTo(9L);

        verify(jdbc).update(anyString(), eq(9L), eq("document:ingest"));
        verify(jdbc).update(anyString(), eq(9L), eq("document:read"));
        verify(audit).recordRequired(eq("role_change"), eq("info"), eq("role.create"), eq("role:9"),
            org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void rejectsProtectedUnknownPlatformAndDuplicateRoles() throws Exception {
        assertThatThrownBy(() -> service.create(request("kma-admin", Set.of())))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(403);

        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq("missing")))
            .thenReturn(List.of());
        assertThatThrownBy(() -> service.create(request("missing-role", Set.of("missing"))))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(400);

        RoleUpsertRequest duplicate = request("duplicate", Set.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("duplicate"), eq("Editor"),
            eq("description"), eq("active"))).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThatThrownBy(() -> service.create(duplicate))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(409);
    }

    @Test
    void updatesRolePermissionsAndInvalidatesMembers() throws Exception {
        stubRole(9L, "editor", false);
        stubPermission("space:update", "space:read", "application", true, true);
        RoleUpsertRequest request = request("editor", Set.of("space:update"));

        service.update(9L, request);

        verify(jdbc).update(anyString(), eq("Editor"), eq("description"), eq("active"), eq(9L));
        verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), eq(9L));
        verify(audit).recordRequired(eq("role_change"), eq("warning"), eq("role.update"), eq("role:9"),
            org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void protectsRoleCodeAndBuiltInRoles() throws Exception {
        stubRole(9L, "editor", false);
        assertThatThrownBy(() -> service.update(9L, request("changed", Set.of())))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(409);

        stubRole(1L, "kma-admin", true);
        assertThatThrownBy(() -> service.update(1L, request("kma-admin", Set.of())))
            .isInstanceOf(KmaException.class).hasMessageContaining("不允许编辑");

        stubRole(2L, "kma-admin", true);
        assertThatThrownBy(() -> service.delete(2L))
            .isInstanceOf(KmaException.class).hasMessageContaining("不可删除");
    }

    @Test
    void deletesUnusedCustomRoleAndRejectsReferencedRole() throws Exception {
        stubRole(9L, "editor", false);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(9L))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("editor"))).thenReturn(0);
        service.delete(9L);
        verify(jdbc).update(anyString(), eq(9L));

        stubRole(10L, "used", false);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(10L))).thenReturn(1);
        assertThatThrownBy(() -> service.delete(10L))
            .isInstanceOf(KmaException.class).hasMessageContaining("用户使用");

        stubRole(11L, "acl-role", false);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(11L))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("acl-role"))).thenReturn(1);
        assertThatThrownBy(() -> service.delete(11L))
            .isInstanceOf(KmaException.class).hasMessageContaining("ACL");
    }

    private RoleUpsertRequest request(String code, Set<String> permissions) {
        RoleUpsertRequest request = new RoleUpsertRequest();
        request.setRoleCode(code);
        request.setName("Editor");
        request.setDescription("description");
        request.setStatus("active");
        request.setPermissions(permissions);
        return request;
    }

    @SuppressWarnings("unchecked")
    private void stubPermission(String code, String parent, String scope, boolean enabled, boolean assignable)
        throws Exception {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(code)))
            .thenAnswer(invocation -> {
                RowMapper<Object> mapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("permission_code")).thenReturn(code);
                when(rs.getString("parent_code")).thenReturn(parent);
                when(rs.getBoolean("enabled")).thenReturn(enabled);
                when(rs.getBoolean("assignable")).thenReturn(assignable);
                return List.of(mapper.mapRow(rs, 0));
            });
    }

    @SuppressWarnings("unchecked")
    private void stubRole(Long id, String code, boolean builtIn) throws Exception {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(id)))
            .thenAnswer(invocation -> {
                RowMapper<Object> mapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("role_code")).thenReturn(code);
                when(rs.getBoolean("built_in")).thenReturn(builtIn);
                return List.of(mapper.mapRow(rs, 0));
            });
    }

    private ResultSet permissionRow(String code, String name, String parent, String type, String scope, int order)
        throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("permission_code")).thenReturn(code);
        when(rs.getString("name")).thenReturn(name);
        when(rs.getString("parent_code")).thenReturn(parent);
        when(rs.getString("permission_type")).thenReturn(type);
        when(rs.getString("description")).thenReturn(name + " description");
        when(rs.getInt("sort_order")).thenReturn(order);
        return rs;
    }
}
