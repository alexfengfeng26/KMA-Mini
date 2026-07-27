package com.kma.common.security;

import com.kma.common.result.ApiResult;
import com.kma.common.security.dto.PermissionNode;
import com.kma.common.security.dto.RoleUpsertRequest;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class RoleAdminController {
    private final KmaRoleAdminService service;

    @GetMapping("/roles")
    @PreAuthorize("@ss.hasAny('role:read','user:role:assign')")
    public ApiResult<List<Map<String, Object>>> list() { return ApiResult.success(service.list()); }

    @PostMapping("/roles")
    @PreAuthorize("@ss.hasPermi('role:create')")
    public ApiResult<Long> create(@Valid @RequestBody RoleUpsertRequest request) {
        return ApiResult.success(service.create(request));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("@ss.hasPermi('role:update')")
    public ApiResult<Void> update(@PathVariable Long roleId, @Valid @RequestBody RoleUpsertRequest request) {
        service.update(roleId, request); return ApiResult.success();
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("@ss.hasPermi('role:delete')")
    public ApiResult<Void> delete(@PathVariable Long roleId) { service.delete(roleId); return ApiResult.success(); }

    @GetMapping("/permissions/tree")
    @PreAuthorize("@ss.hasPermi('permission:read')")
    public ApiResult<List<PermissionNode>> permissionTree() { return ApiResult.success(service.permissionTree()); }
}
