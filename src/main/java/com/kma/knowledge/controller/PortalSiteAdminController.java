package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.PortalConfigDraftRequest;
import com.kma.knowledge.dto.PortalSiteCreateRequest;
import com.kma.knowledge.dto.PortalSiteUpdateRequest;
import com.kma.knowledge.dto.PortalVersionActionRequest;
import com.kma.knowledge.service.PortalSiteService;
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
@RequestMapping("/api/v1/admin/portal-sites")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalSiteAdminController {
    private final PortalSiteService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('portal-site:read')")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.success(service.listSites());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('portal-site:create')")
    public ApiResult<Map<String, Object>> create(@Valid @RequestBody PortalSiteCreateRequest request) {
        return ApiResult.success(service.createSite(request));
    }

    @GetMapping("/{siteKey}")
    @PreAuthorize("@ss.hasPermi('portal-site:read')")
    public ApiResult<Map<String, Object>> get(@PathVariable String siteKey) {
        return ApiResult.success(service.getSite(siteKey));
    }

    @PutMapping("/{siteKey}")
    @PreAuthorize("@ss.hasPermi('portal-site:update')")
    public ApiResult<Map<String, Object>> update(@PathVariable String siteKey,
                                                 @Valid @RequestBody PortalSiteUpdateRequest request) {
        return ApiResult.success(service.updateSite(siteKey, request));
    }

    @DeleteMapping("/{siteKey}")
    @PreAuthorize("@ss.hasPermi('portal-site:delete')")
    public ApiResult<Void> delete(@PathVariable String siteKey) {
        service.deleteSite(siteKey);
        return ApiResult.success();
    }

    @GetMapping("/{siteKey}/versions")
    @PreAuthorize("@ss.hasPermi('portal-site:read')")
    public ApiResult<List<Map<String, Object>>> versions(@PathVariable String siteKey) {
        return ApiResult.success(service.versions(siteKey));
    }

    @GetMapping("/{siteKey}/versions/{versionId}")
    @PreAuthorize("@ss.hasPermi('portal-site:read')")
    public ApiResult<Map<String, Object>> version(@PathVariable String siteKey,
                                                  @PathVariable Long versionId) {
        return ApiResult.success(service.version(siteKey, versionId));
    }

    @PostMapping("/{siteKey}/drafts")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<Map<String, Object>> draft(@PathVariable String siteKey,
                                                @RequestBody(required = false) @Valid PortalConfigDraftRequest request) {
        return ApiResult.success(service.createDraft(siteKey, request));
    }

    @PutMapping("/{siteKey}/drafts/{versionId}")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<Map<String, Object>> updateDraft(@PathVariable String siteKey,
                                                      @PathVariable Long versionId,
                                                      @Valid @RequestBody PortalConfigDraftRequest request) {
        return ApiResult.success(service.updateDraft(siteKey, versionId, request));
    }

    @PostMapping("/{siteKey}/validate")
    @PreAuthorize("@ss.hasPermi('portal-site:read')")
    public ApiResult<Map<String, Object>> validate(@PathVariable String siteKey,
                                                   @Valid @RequestBody PortalVersionActionRequest request) {
        return ApiResult.success(service.validate(siteKey, request));
    }

    @PostMapping("/{siteKey}/submit")
    @PreAuthorize("@ss.hasPermi('portal-site:update')")
    public ApiResult<Void> submit(@PathVariable String siteKey,
                                  @Valid @RequestBody PortalVersionActionRequest request) {
        service.submit(siteKey, request);
        return ApiResult.success();
    }

    @PostMapping("/{siteKey}/approve")
    @PreAuthorize("@ss.hasPermi('portal-site:review')")
    public ApiResult<Void> approve(@PathVariable String siteKey,
                                   @Valid @RequestBody PortalVersionActionRequest request) {
        service.approve(siteKey, request);
        return ApiResult.success();
    }

    @PostMapping("/{siteKey}/reject")
    @PreAuthorize("@ss.hasPermi('portal-site:review')")
    public ApiResult<Void> reject(@PathVariable String siteKey,
                                  @Valid @RequestBody PortalVersionActionRequest request) {
        service.reject(siteKey, request);
        return ApiResult.success();
    }

    @PostMapping("/{siteKey}/publish")
    @PreAuthorize("@ss.hasPermi('portal-site:publish')")
    public ApiResult<Void> publish(@PathVariable String siteKey,
                                   @Valid @RequestBody PortalVersionActionRequest request) {
        service.publish(siteKey, request);
        return ApiResult.success();
    }

    @PostMapping("/{siteKey}/rollback/{versionId}")
    @PreAuthorize("@ss.hasPermi('portal-site:publish')")
    public ApiResult<Map<String, Object>> rollback(@PathVariable String siteKey,
                                                   @PathVariable Long versionId) {
        return ApiResult.success(service.rollback(siteKey, versionId));
    }
}
