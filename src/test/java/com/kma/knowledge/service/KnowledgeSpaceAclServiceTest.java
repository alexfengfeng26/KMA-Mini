package com.kma.knowledge.service;

import com.kma.common.security.KmaPrincipal;
import com.kma.common.security.KmaIdentityContext;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.entity.KnowledgeSpaceAcl;
import com.kma.knowledge.mapper.KnowledgeSpaceAclMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 知识空间 ACL 服务单元测试
 *
 * @author party
 * @date 2026/07/02
 */
class KnowledgeSpaceAclServiceTest {

    @Mock
    private KnowledgeSpaceMapper spaceMapper;
    @Mock
    private KnowledgeSpaceAclMapper aclMapper;
    @Mock
    private Environment environment;

    private KnowledgeSpaceAclService aclService;
    private AutoCloseable closeable;
    private MockedStatic<KmaIdentityContext> identityContextMock;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        aclService = new KnowledgeSpaceAclService(spaceMapper, aclMapper, environment);
        identityContextMock = Mockito.mockStatic(KmaIdentityContext.class);
        when(environment.getProperty("knowledge.acl.default-deny", Boolean.class, true)).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        identityContextMock.close();
        closeable.close();
    }

    @Test
    void shouldDenyWhenSpaceNotExists() {
        when(spaceMapper.selectBySpaceCode("missing")).thenReturn(null);
        assertFalse(aclService.hasReadAccess("missing"));
        assertThrows(AccessDeniedException.class, () -> aclService.assertReadAccess("missing"));
    }

    @Test
    void shouldDenyWhenUserNotLogin() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(null);
        assertFalse(aclService.hasReadAccess("space"));
    }

    @Test
    void shouldAllowSuperAdmin() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(true);
        assertTrue(aclService.hasReadAccess("space"));
    }

    @Test
    void shouldDenyWhenNoAclConfiguredByDefault() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(Collections.emptyList());
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertFalse(aclService.hasReadAccess("space"));
    }

    @Test
    void shouldAllowWhenNoAclConfiguredAndDefaultDenyDisabled() {
        when(environment.getProperty("knowledge.acl.default-deny", Boolean.class, true)).thenReturn(false);
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(Collections.emptyList());
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertTrue(aclService.hasReadAccess("space"));
    }

    @Test
    void shouldAllowUserAclMatch() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("user", "1", "read")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertTrue(aclService.hasReadAccess("space"));
    }

    @Test
    void shouldDenyUserAclMismatch() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("user", "2", "read")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertFalse(aclService.hasReadAccess("space"));
    }

    @Test
    void shouldAllowRoleAclMatch() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("role", "2", "read")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertTrue(aclService.hasReadAccess("space"));
    }

    @Test
    void shouldAllowOrgAclMatch() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("org", "10", "read")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertTrue(aclService.hasReadAccess("space"));
    }

    @Test
    void shouldAllowIngestAclMatch() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("user", "1", "ingest")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertTrue(aclService.hasIngestAccess("space"));
        aclService.assertIngestAccess("space");
    }

    @Test
    void shouldDenyIngestWhenOnlyReadAcl() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("user", "1", "read")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertFalse(aclService.hasIngestAccess("space"));
        assertThrows(AccessDeniedException.class, () -> aclService.assertIngestAccess("space"));
    }

    @Test
    void shouldAllowAdminWhenAdminAclMatch() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "active"));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("user", "1", "admin")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);
        assertTrue(aclService.hasAdminAccess("space"));
    }

    @Test
    void shouldAllowAdminToReactivateDisabledSpaceButDenyReadAndIngest() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace(1L, "space", "disabled"));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("user", "1", "admin")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);

        assertTrue(aclService.hasAdminAccess("space"));
        assertFalse(aclService.hasReadAccess("space"));
        assertFalse(aclService.hasIngestAccess("space"));
    }

    @Test
    void shouldReturnReadableSpaceIds() {
        when(spaceMapper.selectList(any())).thenReturn(List.of(
            buildSpace(1L, "space1", "active"),
            buildSpace(2L, "space2", "active"),
            buildSpace(3L, "space3", "inactive")
        ));
        when(aclMapper.selectList(any())).thenReturn(List.of(buildAcl("user", "1", "read")));
        KmaPrincipal user = buildUser(1L, Set.of(2L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(false);

        Set<Long> readable = aclService.getReadableSpaceIds();
        assertEquals(Set.of(1L, 2L), readable);
    }

    @Test
    void shouldReturnNullReadableSpaceIdsForSuperAdmin() {
        KmaPrincipal user = buildUser(1L, Set.of(1L), 10L);
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::isSuperAdmin).thenReturn(true);
        assertNull(aclService.getReadableSpaceIds());
    }

    private KnowledgeSpace buildSpace(Long spaceId, String code, String status) {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(spaceId);
        space.setSpaceCode(code);
        space.setStatus(status);
        return space;
    }

    private KmaPrincipal buildUser(Long userId, Set<Long> roleIds, Long orgId) {
        KmaPrincipal user = new KmaPrincipal();
        user.setUserId(userId);
        user.setRoleIds(roleIds);
        user.setOrgId(orgId);
        return user;
    }

    private KnowledgeSpaceAcl buildAcl(String principalType, String principalValue, String permission) {
        KnowledgeSpaceAcl acl = new KnowledgeSpaceAcl();
        acl.setPrincipalType(principalType);
        acl.setPrincipalValue(principalValue);
        acl.setPermission(permission);
        return acl;
    }
}




