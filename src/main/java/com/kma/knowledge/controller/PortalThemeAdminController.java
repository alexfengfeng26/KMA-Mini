package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.PortalThemeFilesRequest;
import com.kma.knowledge.dto.PortalThemeImmediatePublishRequest;
import com.kma.knowledge.dto.PortalThemeDesignProposalRequest;
import com.kma.knowledge.dto.PortalThemeDesignProposalResponse;
import com.kma.knowledge.service.PortalThemeDesignService;
import com.kma.knowledge.service.PortalThemeService;
import com.kma.knowledge.service.PortalThemeReleaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/portal-sites/{siteKey}/theme-workspace")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalThemeAdminController {
    private final PortalThemeService service;
    private final PortalThemeDesignService designService;
    private final PortalThemeReleaseService releaseService;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<Map<String, Object>> workspace(@PathVariable String siteKey,
                                                     @RequestParam(required = false) String themeKey) {
        return ApiResult.success(service.workspace(siteKey, themeKey));
    }

    @GetMapping("/themes")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<java.util.List<Map<String, Object>>> themes(@PathVariable String siteKey) {
        return ApiResult.success(service.themes(siteKey));
    }

    @PutMapping("/{themeVersionId}")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit') and @ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> save(@PathVariable String siteKey,
                                               @PathVariable Long themeVersionId,
                                               @Valid @RequestBody PortalThemeFilesRequest request) {
        return ApiResult.success(service.save(siteKey, themeVersionId, request));
    }

    @PostMapping("/{themeVersionId}/apply")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit') and @ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> apply(@PathVariable String siteKey,
                                                 @PathVariable Long themeVersionId) {
        return ApiResult.success(service.applyTheme(siteKey, themeVersionId));
    }

    @PostMapping("/{themeKey}/local-source/sync")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit') and @ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> syncLocalSource(@PathVariable String siteKey,
                                                           @PathVariable String themeKey) {
        return ApiResult.success(service.syncLocalSource(siteKey, themeKey));
    }

    @PostMapping("/{themeKey}/publish-immediately")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit') " +
        "and @ss.hasPermi('portal-code:edit') and @ss.hasPermi('portal-site:publish')")
    public ApiResult<Map<String, Object>> publishImmediately(@PathVariable String siteKey,
                                                               @PathVariable String themeKey,
                                                               @Valid @RequestBody PortalThemeImmediatePublishRequest request) {
        return ApiResult.success(releaseService.publishImmediately(siteKey, themeKey, request));
    }

    @PostMapping("/{themeVersionId}/ai-proposals")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit') and @ss.hasPermi('portal-code:edit')")
    public ApiResult<PortalThemeDesignProposalResponse> propose(
        @PathVariable String siteKey,
        @PathVariable Long themeVersionId,
        @Valid @RequestBody PortalThemeDesignProposalRequest request) {
        return ApiResult.success(designService.propose(siteKey, themeVersionId, request));
    }

    @GetMapping("/{themeVersionId}/export")
    @PreAuthorize("@ss.hasPermi('portal-code:read')")
    public ResponseEntity<byte[]> export(
        @PathVariable String siteKey, @PathVariable Long themeVersionId) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(siteKey + "-theme-v" + themeVersionId + ".zip", StandardCharsets.UTF_8)
                .build().toString())
            .body(service.exportZip(siteKey, themeVersionId));
    }

    @PostMapping(path = "/{themeVersionId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit') and @ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> importTheme(
        @PathVariable String siteKey,
        @PathVariable Long themeVersionId,
        @RequestParam Integer expectedLockVersion,
        @RequestPart("file") MultipartFile file) {
        return ApiResult.success(service.importZip(siteKey, themeVersionId, expectedLockVersion, file));
    }

    @GetMapping("/diff")
    @PreAuthorize("@ss.hasPermi('portal-code:read')")
    public ApiResult<Map<String, Object>> diff(
        @PathVariable String siteKey,
        @RequestParam Long fromVersionId,
        @RequestParam Long toVersionId) {
        return ApiResult.success(service.diff(siteKey, fromVersionId, toVersionId));
    }
}
