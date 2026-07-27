package com.kma.knowledge.service;

import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaPrincipal;
import com.kma.knowledge.dto.SpaceAclView;
import com.kma.knowledge.entity.KnowledgeSpaceAcl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclPrincipalValidatorTest {
    @Mock private JdbcTemplate jdbc;
    private AclPrincipalValidator validator;

    @BeforeEach
    void setUp() {
        KmaPrincipal principal = new KmaPrincipal();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        validator = new AclPrincipalValidator(jdbc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validatesEachSupportedPrincipalTypeAndRejectsInvalidInput() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("7"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("reader"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("branch"))).thenReturn(1);
        assertThatCode(() -> validator.validate("user", "7")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("role", "reader")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("org", "branch")).doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validate(null, "x"))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(400);
        assertThatThrownBy(() -> validator.validate("group", "x"))
            .isInstanceOf(KmaException.class).hasMessageContaining("不支持");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("disabled"))).thenReturn(0);
        assertThatThrownBy(() -> validator.validate("role", "disabled"))
            .isInstanceOf(KmaException.class).hasMessageContaining("不存在或已停用");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsTypedOptionsWithTrimmedSearch() throws Exception {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
            eq("alice"), eq("alice"), eq("alice")))
            .thenAnswer(invocation -> List.of(option(invocation.getArgument(1), "7", "Alice", "alice")));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
            eq("reader"), eq("reader"), eq("reader")))
            .thenAnswer(invocation -> List.of(option(invocation.getArgument(1), "reader", "Reader", "reader")));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
            eq("branch"), eq("branch"), eq("branch")))
            .thenAnswer(invocation -> List.of(option(invocation.getArgument(1), "branch", "Branch", "branch")));

        assertThat(validator.list("user", " alice ").get(0)).containsEntry("type", "user");
        assertThat(validator.list("role", "reader").get(0)).containsEntry("type", "role");
        assertThat(validator.list("org", "branch").get(0)).containsEntry("type", "org");
        assertThatThrownBy(() -> validator.list("group", null)).isInstanceOf(KmaException.class);
    }

    @Test
    void convertsAclToViewUsingDisplayNamesAndFallsBackToCode() {
        KnowledgeSpaceAcl userAcl = acl("user", "7");
        when(jdbc.queryForList(anyString(), eq(String.class), eq("7")))
            .thenReturn(List.of("Alice"));
        SpaceAclView userView = validator.toView(userAcl);
        assertThat(userView.principalDisplayName()).isEqualTo("Alice");

        KnowledgeSpaceAcl roleAcl = acl("role", "reader");
        when(jdbc.queryForList(anyString(), eq(String.class), eq("reader")))
            .thenReturn(List.of("Reader"));
        assertThat(validator.toView(roleAcl).principalDisplayName()).isEqualTo("Reader");

        KnowledgeSpaceAcl orgAcl = acl("org", "branch");
        when(jdbc.queryForList(anyString(), eq(String.class), eq("branch")))
            .thenReturn(List.of());
        assertThat(validator.toView(orgAcl).principalDisplayName()).isEqualTo("branch");

        KnowledgeSpaceAcl legacy = acl("legacy", "old");
        assertThat(validator.toView(legacy).principalDisplayName()).isEqualTo("old");
    }

    private Map<String, Object> option(RowMapper<Map<String, Object>> mapper, String value, String label,
                                       String secondary) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("value")).thenReturn(value);
        when(rs.getString("label")).thenReturn(label);
        when(rs.getString("secondary")).thenReturn(secondary);
        return mapper.mapRow(rs, 0);
    }

    private KnowledgeSpaceAcl acl(String type, String value) {
        KnowledgeSpaceAcl acl = new KnowledgeSpaceAcl();
        acl.setAclId(1L);
        acl.setSpaceId(2L);
        acl.setPrincipalType(type);
        acl.setPrincipalValue(value);
        acl.setPermission("read");
        acl.setCreateTime(LocalDateTime.now());
        return acl;
    }
}
