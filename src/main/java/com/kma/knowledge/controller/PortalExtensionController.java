package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.PortalExtensionReleaseRequest;
import com.kma.knowledge.service.PortalExtensionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Platform extension registry: normal admins can inspect the catalog; only CI identities can release packages. */
@RestController
@RequiredArgsConstructor
public class PortalExtensionController {
    private final PortalExtensionService service;

    @GetMapping("/api/v1/admin/portal-extensions")
    @PreAuthorize("@ss.hasPermi('portal-extension:read')")
    public ApiResult<List<Map<String, Object>>> catalog() {
        return ApiResult.success(service.catalog());
    }

    @PostMapping("/api/v1/platform/portal-extensions/releases")
    @PreAuthorize("@ss.hasPermi('portal-extension:release')")
    public ApiResult<Map<String, Object>> release(@Valid @RequestBody PortalExtensionReleaseRequest request) {
        return ApiResult.success(service.release(request));
    }
}
