package com.kma.common.security;

import com.kma.common.exception.KmaException;
import com.kma.common.security.dto.OrganizationCreateRequest;
import com.kma.common.security.dto.OrganizationMoveRequest;
import com.kma.common.security.dto.OrganizationNode;
import com.kma.common.security.dto.OrganizationUpdateRequest;
import com.kma.common.security.dto.UserOrganizationsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KmaOrganizationServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private SecurityAuditService audit;
    private KmaOrganizationService service;

    @BeforeEach
    void setUp() {
        KmaPrincipal principal = new KmaPrincipal();
        principal.setUserId(1L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        service = new KmaOrganizationService(jdbc, audit);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsTreeAndReadsMembersAndUserOrganizations() throws Exception {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<OrganizationNode>>any()))
            .thenAnswer(invocation -> {
                RowMapper<OrganizationNode> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(orgRow(1L, "root", "Root", null, true), 0),
                    mapper.mapRow(orgRow(2L, "child", "Child", 1L, false), 1));
            });
        stubOrg(2L, "child", false);
        when(jdbc.queryForList(anyString(), eq(2L)))
            .thenReturn(List.of(Map.of("username", "alice")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(7L)))
            .thenReturn(List.of(Map.of("org_code", "child")));

        List<OrganizationNode> tree = service.tree();
        assertThat(tree).singleElement().satisfies(root ->
            assertThat(root.children()).extracting(OrganizationNode::orgCode).containsExactly("child"));
        assertThat(service.members(2L).get(0)).containsEntry("username", "alice");
        assertThat(service.userOrganizations(7L).get(0)).containsEntry("org_code", "child");
    }

    @Test
    void createsOrganizationAndRebuildsClosure() throws Exception {
        stubOrg(1L, "root", true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("branch"), eq("Branch"),
            eq(1L), eq(5))).thenReturn(3L);

        assertThat(service.create(new OrganizationCreateRequest("branch", "Branch", 1L, 5))).isEqualTo(3L);

        verify(jdbc, times(2)).update(anyString());
        verify(audit).recordRequired(eq("organization_change"), eq("info"), eq("org.create"), eq("org:3"),
            org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void translatesDuplicateOrganizationToConflict() throws Exception {
        stubOrg(1L, "root", true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("branch"), eq("Branch"),
            eq(1L), eq(0))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(new OrganizationCreateRequest("branch", "Branch", 1L, null)))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(409);
    }

    @Test
    void updatesOrganizationAndInvalidatesDescendantMembers() throws Exception {
        stubOrg(2L, "branch", false);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(2L)))
            .thenReturn(List.of(2L, 3L));

        service.update(2L, new OrganizationUpdateRequest("Renamed", null, null));

        verify(jdbc).update(anyString(), eq("Renamed"), eq("active"), eq(0), eq(2L));
        verify(jdbc).update(anyString(), eq("2,3"));
        verify(audit).recordRequired(eq("organization_change"), eq("warning"), eq("org.update"), eq("org:2"),
            org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void movesOrganizationAndRejectsRootSelfAndDescendantCycles() throws Exception {
        stubOrg(2L, "branch", false);
        stubOrg(1L, "root", true);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(2L), eq(1L))).thenReturn(0);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(2L))).thenReturn(List.of(2L, 3L));
        service.move(2L, new OrganizationMoveRequest(1L));
        verify(jdbc).update(anyString(), eq(1L), eq(2L));

        stubOrg(4L, "root", true);
        stubOrg(1L, "parent", false);
        assertThatThrownBy(() -> service.move(4L, new OrganizationMoveRequest(1L)))
            .isInstanceOf(KmaException.class).hasMessageContaining("不可移动");

        stubOrg(5L, "self", false);
        assertThatThrownBy(() -> service.move(5L, new OrganizationMoveRequest(5L)))
            .isInstanceOf(KmaException.class).hasMessageContaining("自身下级");

        stubOrg(6L, "parent", false);
        stubOrg(7L, "child", false);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(6L), eq(7L))).thenReturn(1);
        assertThatThrownBy(() -> service.move(6L, new OrganizationMoveRequest(7L)))
            .isInstanceOf(KmaException.class).hasMessageContaining("自身下级");
    }

    @Test
    void deletesOnlyUnusedNonRootOrganization() throws Exception {
        stubOrg(2L, "branch", false);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(2L))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("branch"))).thenReturn(0);
        service.delete(2L);
        verify(jdbc).update(anyString(), eq(2L));

        stubOrg(3L, "used", false);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(3L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("used"))).thenReturn(0);
        assertThatThrownBy(() -> service.delete(3L))
            .isInstanceOf(KmaException.class).hasMessageContaining("不能删除");

        stubOrg(1L, "root", true);
        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(KmaException.class).hasMessageContaining("不可删除");
    }

    @Test
    void assignsMultipleOrganizationsAndRequiresPrimaryToBeSelected() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(9L))).thenReturn(1);
        assertThatThrownBy(() -> service.setUserOrganizations(9L,
            new UserOrganizationsRequest(Set.of(2L), 3L)))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(400);

        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(2);
        service.setUserOrganizations(9L, new UserOrganizationsRequest(Set.of(2L, 3L), 2L));
        verify(jdbc).update(anyString(), eq(9L), eq(2L), eq(true));
        verify(jdbc).update(anyString(), eq(9L), eq(3L), eq(false));
        verify(audit).recordRequired(eq("organization_membership_change"), eq("warning"), eq("org.members.update"),
            eq("user:9"), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void validatesOrganizationsAndExpandsAncestorCodes() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(9L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("2"))).thenReturn(0);
        assertThatThrownBy(() -> service.setUserOrganizations(9L,
            new UserOrganizationsRequest(Set.of(2L), 2L)))
            .isInstanceOf(KmaException.class).hasMessageContaining("不存在或已停用");

        assertThatThrownBy(() -> service.setUserOrganizations(9L,
            new UserOrganizationsRequest(Set.of(2L), null)))
            .isInstanceOf(KmaException.class).hasMessageContaining("主组织");

        assertThat(service.ancestorCodes(Set.of())).isEmpty();
        when(jdbc.queryForList(anyString(), eq(String.class), eq("child")))
            .thenReturn(List.of("root", "child"));
        assertThat(service.ancestorCodes(Set.of("child"))).containsExactly("root", "child");
    }

    @Test
    void emptyOrganizationSelectionFallsBackToRoot() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(9L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("1"))).thenReturn(1);

        service.setUserOrganizations(9L, new UserOrganizationsRequest(Set.of(), null));

        verify(jdbc).update(anyString(), eq(9L), eq(1L), eq(true));
    }

    @SuppressWarnings("unchecked")
    private void stubOrg(Long id, String code, boolean builtIn) throws Exception {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(id)))
            .thenAnswer(invocation -> {
                RowMapper<Object> mapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("org_code")).thenReturn(code);
                when(rs.getBoolean("built_in")).thenReturn(builtIn);
                return List.of(mapper.mapRow(rs, 0));
            });
    }

    private ResultSet orgRow(Long id, String code, String name, Long parent, boolean builtIn) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("org_id")).thenReturn(id);
        when(rs.getString("org_code")).thenReturn(code);
        when(rs.getString("name")).thenReturn(name);
        when(rs.getObject("parent_id")).thenReturn(parent);
        when(rs.getString("status")).thenReturn("active");
        when(rs.getBoolean("built_in")).thenReturn(builtIn);
        when(rs.getInt("sort_order")).thenReturn(0);
        when(rs.getLong("member_count")).thenReturn(0L);
        return rs;
    }
}
