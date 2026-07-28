package com.kma.knowledge.service;

import com.kma.common.security.KmaPrincipal;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.KmaOrganizationService;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.entity.KnowledgeSpaceAcl;
import com.kma.knowledge.enums.SpaceStatus;
import com.kma.knowledge.mapper.KnowledgeSpaceAclMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识空间 ACL 校验服务
 *
 * @author party
 * @date 2026/07/02
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeSpaceAclService {

    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeSpaceAclMapper aclMapper;
    private final Environment environment;
    private final KmaOrganizationService organizationService;

    public KnowledgeSpaceAclService(KnowledgeSpaceMapper spaceMapper, KnowledgeSpaceAclMapper aclMapper,
                                    Environment environment) {
        this(spaceMapper, aclMapper, environment, null);
    }

    @Autowired
    public KnowledgeSpaceAclService(KnowledgeSpaceMapper spaceMapper, KnowledgeSpaceAclMapper aclMapper,
                                    Environment environment, KmaOrganizationService organizationService) {
        this.spaceMapper = spaceMapper;
        this.aclMapper = aclMapper;
        this.environment = environment;
        this.organizationService = organizationService;
    }

    /**
     * 校验当前用户是否对指定空间具有读权限
     *
     * @param spaceCode 空间编码
     * @throws AccessDeniedException 无权限时抛出
     */
    public void assertReadAccess(String spaceCode) {
        if (!hasReadAccess(spaceCode)) {
            throw new AccessDeniedException("无权限访问知识空间: " + spaceCode);
        }
    }

    /**
     * 判断当前用户是否对指定空间具有读权限
     */
    public boolean hasReadAccess(String spaceCode) {
        return hasReadAccess(spaceCode, KmaIdentityContext.getLoginUser());
    }

    /** Evaluates a captured identity, allowing a long-running stream to recheck ACLs safely. */
    public boolean hasReadAccess(String spaceCode, KmaPrincipal user) {
        if ("*".equals(spaceCode)) {
            Set<Long> readable = getReadableSpaceIds(user);
            return readable == null || !readable.isEmpty();
        }
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(spaceCode);
        if (space == null) {
            return false;
        }
        return hasReadAccess(space, user);
    }

    /**
     * 判断当前用户是否对指定空间具有读权限
     */
    public boolean hasReadAccess(KnowledgeSpace space) {
        return hasReadAccess(space, KmaIdentityContext.getLoginUser());
    }

    private boolean hasReadAccess(KnowledgeSpace space, KmaPrincipal user) {
        if (space == null) {
            return false;
        }
        if (!SpaceStatus.ACTIVE.getCode().equals(space.getStatus())) {
            return false;
        }

        if (user == null) {
            return false;
        }
        if (isSuperAdmin(user)) {
            return true;
        }

        List<KnowledgeSpaceAcl> acls = aclMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeSpaceAcl>()
                .eq(KnowledgeSpaceAcl::getSpaceId, space.getSpaceId())
        );
        // 未配置 ACL 时，默认拒绝访问（可通过 knowledge.acl.default-deny=false 恢复兼容）
        if (acls == null || acls.isEmpty()) {
            return !isDefaultDeny();
        }

        return acls.stream().anyMatch(acl -> match(acl, user) && hasPermission(acl.getPermission(), "read"));
    }

    /**
     * 校验当前用户是否对指定空间具有摄入权限
     */
    public void assertIngestAccess(String spaceCode) {
        if (!hasIngestAccess(spaceCode)) {
            throw new AccessDeniedException("无权限向知识空间摄入文档: " + spaceCode);
        }
    }

    /**
     * 判断当前用户是否对指定空间具有摄入权限
     */
    public boolean hasIngestAccess(String spaceCode) {
        return checkSpacePermission(spaceCode, "ingest");
    }

    /**
     * 校验当前用户是否对指定空间具有管理权限
     */
    public void assertAdminAccess(String spaceCode) {
        if (!hasAdminAccess(spaceCode)) {
            throw new AccessDeniedException("无权限管理知识空间: " + spaceCode);
        }
    }

    /**
     * 判断当前用户是否对指定空间具有管理权限
     */
    public boolean hasAdminAccess(String spaceCode) {
        return checkSpacePermission(spaceCode, "admin");
    }

    private boolean checkSpacePermission(String spaceCode, String requiredPermission) {
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(spaceCode);
        if (space == null) {
            return false;
        }
        if (!SpaceStatus.ACTIVE.getCode().equals(space.getStatus()) && !"admin".equals(requiredPermission)) {
            return false;
        }

        KmaPrincipal user = KmaIdentityContext.getLoginUser();
        if (user == null) {
            return false;
        }
        if (isSuperAdmin(user)) {
            return true;
        }

        List<KnowledgeSpaceAcl> acls = aclMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeSpaceAcl>()
                .eq(KnowledgeSpaceAcl::getSpaceId, space.getSpaceId())
        );
        // 未配置 ACL 时，默认拒绝访问（可通过 knowledge.acl.default-deny=false 恢复兼容）
        if (acls == null || acls.isEmpty()) {
            return !isDefaultDeny();
        }

        return acls.stream().anyMatch(acl -> match(acl, user)
            && (acl.getPermission().equals(requiredPermission) || "admin".equals(acl.getPermission())));
    }

    /**
     * 获取当前用户可读的知识空间 ID 集合
     * <p>
     * 超级管理员返回 null（表示不过滤）；未登录返回空集合。
     */
    public Set<Long> getReadableSpaceIds() {
        return getReadableSpaceIds(KmaIdentityContext.getLoginUser());
    }

    private Set<Long> getReadableSpaceIds(KmaPrincipal user) {
        if (user == null) {
            return Collections.emptySet();
        }
        if (KmaIdentityContext.isSuperAdmin()) {
            return null;
        }

        List<KnowledgeSpace> spaces = spaceMapper.selectList(null);
        return spaces.stream()
            .filter(space -> SpaceStatus.ACTIVE.getCode().equals(space.getStatus()))
            .filter(space -> hasReadAccess(space, user))
            .map(KnowledgeSpace::getSpaceId)
            .collect(Collectors.toSet());
    }

    /** Active spaces readable by the current identity, including all active spaces for platform administrators. */
    public List<KnowledgeSpace> getReadableSpaces() {
        KmaPrincipal user = KmaIdentityContext.getLoginUser();
        if (user == null) return List.of();
        List<KnowledgeSpace> spaces = spaceMapper.selectList(null);
        return spaces.stream()
            .filter(space -> SpaceStatus.ACTIVE.getCode().equals(space.getStatus()))
            .filter(space -> isSuperAdmin(user) || hasReadAccess(space, user))
            .toList();
    }

    private boolean match(KnowledgeSpaceAcl acl, KmaPrincipal user) {
        return switch (acl.getPrincipalType()) {
            case "user" -> "user".equalsIgnoreCase(user.getSubjectType())
                && (Objects.equals(user.getSubjectId(), acl.getPrincipalValue())
                    || Objects.equals(user.getUserId(), parseLong(acl.getPrincipalValue())));
            case "role" -> user.getRoles().contains(acl.getPrincipalValue())
                || matchAnyRole(user.getRoleIds(), acl.getPrincipalValue());
            case "org" -> organizationCodes(user).contains(acl.getPrincipalValue())
                || Objects.equals(user.getOrgId(), parseLong(acl.getPrincipalValue()));
            default -> false;
        };
    }

    private boolean matchAnyRole(Set<Long> roleIds, String principalValue) {
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        Long target = parseLong(principalValue);
        return target != null && roleIds.contains(target);
    }

    private boolean hasPermission(String aclPermission, String required) {
        if (aclPermission == null) {
            return false;
        }
        if (aclPermission.equals(required) || aclPermission.equals("admin")) return true;
        return "read".equals(required) && "ingest".equals(aclPermission);
    }

    private Set<String> organizationCodes(KmaPrincipal user) {
        List<String> direct = user.getOrganizationCodes();
        if (direct == null || direct.isEmpty()) direct = user.getOrgIds();
        if (organizationService == null || direct == null || direct.isEmpty()) {
            return direct == null ? Set.of() : Set.copyOf(direct);
        }
        return organizationService.ancestorCodes(direct);
    }

    private Long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("ACL principalValue 无法解析为数值: {}", value);
            return null;
        }
    }

    private boolean isDefaultDeny() {
        return environment.getProperty("knowledge.acl.default-deny", Boolean.class, true);
    }

    private boolean isSuperAdmin(KmaPrincipal user) {
        // Normal HTTP calls retain the established identity-context shortcut.
        // Long-running streams pass a captured principal after their request
        // context has gone away, so retain the principal-based check as well.
        return KmaIdentityContext.isSuperAdmin() || (user != null && (user.getPermissions().contains("kma:admin")
            || user.getRoles().contains("kma-admin")));
    }
}



