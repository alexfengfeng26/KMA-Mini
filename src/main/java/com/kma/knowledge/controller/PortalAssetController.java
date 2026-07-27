package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.service.PortalAssetService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalAssetController {
    private final PortalAssetService service;

    @GetMapping("/api/v1/admin/portal-assets/{siteKey}")
    @PreAuthorize("@ss.hasPermi('portal-site:read')")
    public ApiResult<List<Map<String, Object>>> list(@PathVariable String siteKey) {
        return ApiResult.success(service.list(siteKey));
    }

    @PostMapping("/api/v1/admin/portal-assets/{siteKey}")
    @PreAuthorize("@ss.hasPermi('portal-asset:manage')")
    public ApiResult<Map<String, Object>> upload(
        @PathVariable String siteKey,
        @RequestParam @Pattern(regexp = "logo|favicon|background|icon|illustration") String assetType,
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResult.success(service.upload(siteKey, assetType, file));
    }

    @DeleteMapping("/api/v1/admin/portal-assets/{siteKey}/{assetId}")
    @PreAuthorize("@ss.hasPermi('portal-asset:manage')")
    public ApiResult<Void> delete(@PathVariable String siteKey, @PathVariable Long assetId) {
        service.delete(siteKey, assetId);
        return ApiResult.success();
    }

    @GetMapping("/api/v1/portal-sites/{siteKey}/assets/{assetKey}")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ResponseEntity<InputStreamResource> asset(@PathVariable String siteKey,
                                                      @PathVariable String assetKey) throws Exception {
        PortalAssetService.Asset asset = service.open(siteKey, assetKey);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(asset.originalName(), StandardCharsets.UTF_8)
                    .build().toString())
            .body(new InputStreamResource(service.stream(asset)));
    }
}
