package com.kma.common.security;

import com.kma.common.result.ApiResult;
import com.kma.common.result.PageResult;
import com.kma.common.security.dto.OrganizationCreateRequest;
import com.kma.common.security.dto.OrganizationMemberAddRequest;
import com.kma.common.security.dto.OrganizationMoveRequest;
import com.kma.common.security.dto.OrganizationNode;
import com.kma.common.security.dto.OrganizationUpdateRequest;
import com.kma.common.security.dto.UserOrganizationsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class OrganizationAdminController {
    private final KmaOrganizationService service;

    @GetMapping("/organizations/tree")
    @PreAuthorize("@ss.hasPermi('org:read')")
    public ApiResult<List<OrganizationNode>> tree() { return ApiResult.success(service.tree()); }

    @PostMapping("/organizations")
    @PreAuthorize("@ss.hasPermi('org:create')")
    public ApiResult<Long> create(@Valid @RequestBody OrganizationCreateRequest request) {
        return ApiResult.success(service.create(request));
    }

    @PutMapping("/organizations/{orgId}")
    @PreAuthorize("@ss.hasPermi('org:update')")
    public ApiResult<Void> update(@PathVariable Long orgId, @Valid @RequestBody OrganizationUpdateRequest request) {
        service.update(orgId, request); return ApiResult.success();
    }

    @PutMapping("/organizations/{orgId}/move")
    @PreAuthorize("@ss.hasPermi('org:move')")
    public ApiResult<Void> move(@PathVariable Long orgId, @Valid @RequestBody OrganizationMoveRequest request) {
        service.move(orgId, request); return ApiResult.success();
    }

    @DeleteMapping("/organizations/{orgId}")
    @PreAuthorize("@ss.hasPermi('org:delete')")
    public ApiResult<Void> delete(@PathVariable Long orgId) { service.delete(orgId); return ApiResult.success(); }

    @GetMapping("/organizations/{orgId}/members")
    @PreAuthorize("@ss.hasPermi('org:read')")
    public ApiResult<List<Map<String, Object>>> members(@PathVariable Long orgId) {
        return ApiResult.success(service.members(orgId));
    }

    @PostMapping("/organizations/{orgId}/members")
    @PreAuthorize("@ss.hasPermi('org:member:manage')")
    public ApiResult<Void> addMembers(@PathVariable Long orgId,
        @Valid @RequestBody OrganizationMemberAddRequest request) {
        service.addMembers(orgId, request.userIds(), request.primary());
        return ApiResult.success();
    }

    @DeleteMapping("/organizations/{orgId}/members/{userId}")
    @PreAuthorize("@ss.hasPermi('org:member:manage')")
    public ApiResult<Void> removeMember(@PathVariable Long orgId, @PathVariable Long userId) {
        service.removeMember(orgId, userId);
        return ApiResult.success();
    }

    @GetMapping("/organizations/{orgId}/members/page")
    @PreAuthorize("@ss.hasPermi('org:read')")
    public ApiResult<PageResult<Map<String, Object>>> memberPage(
        @PathVariable Long orgId,
        @RequestParam(defaultValue = "1") @Min(1) int pageNum,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "username") String sortBy,
        @RequestParam(defaultValue = "asc") String sortOrder) {
        return ApiResult.success(service.memberPage(orgId, pageNum, pageSize, keyword, sortBy, sortOrder));
    }

    @GetMapping("/users/{userId}/organizations")
    @PreAuthorize("@ss.hasAny('org:read','user:read')")
    public ApiResult<List<Map<String, Object>>> userOrganizations(@PathVariable Long userId) {
        return ApiResult.success(service.userOrganizations(userId));
    }

    @PutMapping("/users/{userId}/organizations")
    @PreAuthorize("@ss.hasPermi('org:member:manage')")
    public ApiResult<Void> setUserOrganizations(@PathVariable Long userId,
        @Valid @RequestBody UserOrganizationsRequest request) {
        service.setUserOrganizations(userId, request); return ApiResult.success();
    }
}
