package com.kma.common.security;

import com.kma.common.result.ApiResult;
import com.kma.common.result.PageResult;
import com.kma.common.exception.KmaException;
import com.kma.common.security.dto.ResetPasswordRequest;
import com.kma.common.security.dto.UserCreateRequest;
import com.kma.common.security.dto.RoleUpsertRequest;
import com.kma.common.security.dto.UserRolesRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class UserAdminController {
    private final KmaUserAdminService userAdminService;
    private final KmaRoleAdminService roleAdminService;

    public UserAdminController(KmaUserAdminService userAdminService, KmaRoleAdminService roleAdminService) {
        this.userAdminService = userAdminService;
        this.roleAdminService = roleAdminService;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('user:read')")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.success(userAdminService.list());
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermi('user:read')")
    public ApiResult<PageResult<Map<String, Object>>> page(
        @RequestParam(defaultValue = "1") @Min(1) int pageNum,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "createTime") String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder) {
        return ApiResult.success(userAdminService.page(pageNum, pageSize, keyword, sortBy, sortOrder));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('user:create')")
    public ApiResult<Long> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResult.success(userAdminService.create(request));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("@ss.hasPermi('user:status:update')")
    public ApiResult<Void> status(@PathVariable Long userId,
        @RequestParam @Pattern(regexp = "active|disabled") String status) {
        userAdminService.changeStatus(userId, status);
        return ApiResult.success();
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("@ss.hasPermi('user:password:reset')")
    public ApiResult<Void> resetPassword(@PathVariable Long userId,
        @Valid @RequestBody(required = false) ResetPasswordRequest request,
        @RequestParam(required = false) @Size(min = 12, max = 256) String newPassword) {
        String effectivePassword = request == null ? newPassword : request.getNewPassword();
        if (effectivePassword == null || effectivePassword.isBlank()) {
            throw new KmaException(400, "新密码不能为空");
        }
        userAdminService.resetPassword(userId, effectivePassword);
        return ApiResult.success();
    }

    @GetMapping("/roles")
    @PreAuthorize("@ss.hasPermi('role:read')")
    public ApiResult<List<Map<String, Object>>> roles() { return ApiResult.success(userAdminService.listRoles()); }

    @GetMapping("/permissions")
    @PreAuthorize("@ss.hasPermi('permission:read')")
    public ApiResult<List<Map<String, Object>>> permissions() { return ApiResult.success(userAdminService.listPermissions()); }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("@ss.hasPermi('user:role:assign')")
    public ApiResult<Void> updateRoles(@PathVariable Long userId, @Valid @RequestBody UserRolesRequest request) {
        userAdminService.updateRoles(userId, request); return ApiResult.success();
    }

    @PostMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<Long> upsertRole(@Valid @RequestBody RoleUpsertRequest request) {
        List<Map<String, Object>> roles = roleAdminService.list();
        Map<String, Object> existing = roles.stream()
            .filter(role -> request.getRoleCode().equals(role.get("roleCode"))).findFirst().orElse(null);
        if (existing == null) {
            requirePermission("role:create");
            return ApiResult.success(roleAdminService.create(request));
        }
        requirePermission("role:update");
        roleAdminService.update(((Number) existing.get("roleId")).longValue(), request);
        return ApiResult.success(((Number) existing.get("roleId")).longValue());
    }

    @PostMapping("/{userId}/revoke-tokens")
    @PreAuthorize("@ss.hasPermi('user:token:revoke')")
    public ApiResult<Void> revokeTokens(@PathVariable Long userId) {
        userAdminService.revokeTokens(userId); return ApiResult.success();
    }

    private void requirePermission(String permission) {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        if (principal == null || !KmaPermissionCatalog.has(principal.getPermissions(), permission)) {
            throw new AccessDeniedException("缺少权限: " + permission);
        }
    }
}
