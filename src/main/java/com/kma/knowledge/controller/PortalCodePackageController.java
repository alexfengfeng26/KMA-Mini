package com.kma.knowledge.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.PortalCodeFilesRequest;
import com.kma.knowledge.dto.PortalCodePackageRequest;
import com.kma.knowledge.service.PortalCodePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class PortalCodePackageController {
    private final PortalCodePackageService service;
    private final ObjectMapper objectMapper;

    @Value("${kma.portal-app-origin:'self'}")
    private String portalAppOrigin;

    @GetMapping("/api/v1/admin/portal-code-packages")
    @PreAuthorize("@ss.hasPermi('portal-code:read')")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.success(service.list());
    }

    @PostMapping("/api/v1/admin/portal-code-packages")
    @PreAuthorize("@ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> create(@Valid @RequestBody PortalCodePackageRequest request) {
        return ApiResult.success(service.create(request));
    }

    @GetMapping("/api/v1/admin/portal-code-packages/{packageId}")
    @PreAuthorize("@ss.hasPermi('portal-code:read')")
    public ApiResult<Map<String, Object>> get(@PathVariable Long packageId) {
        return ApiResult.success(service.get(packageId));
    }

    @PutMapping("/api/v1/admin/portal-code-packages/{packageId}")
    @PreAuthorize("@ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> update(@PathVariable Long packageId,
                                                 @Valid @RequestBody PortalCodePackageRequest request) {
        return ApiResult.success(service.update(packageId, request));
    }

    @PostMapping(value = "/api/v1/admin/portal-code-packages/{packageId}/versions/{version}/files",
        consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> saveFiles(@PathVariable Long packageId,
                                                    @PathVariable String version,
                                                    @Valid @RequestBody PortalCodeFilesRequest request) {
        if (!version.equals(request.getVersion())) throw new KmaException(400, "PORTAL_CODE_VERSION_MISMATCH");
        return ApiResult.success(service.saveEditorFiles(packageId, request));
    }

    @PostMapping(value = "/api/v1/admin/portal-code-packages/{packageId}/versions/{version}/files",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> importZip(@PathVariable Long packageId,
                                                    @PathVariable String version,
                                                    @RequestPart(value = "manifest", required = false) String manifest,
                                                    @RequestPart("file") MultipartFile file) {
        try {
            JsonNode value = manifest == null || manifest.isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(manifest);
            return ApiResult.success(service.importZip(packageId, version, value, file));
        } catch (Exception ex) {
            if (ex instanceof KmaException kmaException) throw kmaException;
            throw new KmaException(400, "PORTAL_CODE_MANIFEST_INVALID");
        }
    }

    @PostMapping("/api/v1/admin/portal-code-packages/{packageId}/scan/{versionId}")
    @PreAuthorize("@ss.hasPermi('portal-code:edit')")
    public ApiResult<Map<String, Object>> scan(@PathVariable Long packageId, @PathVariable Long versionId) {
        return ApiResult.success(service.scan(packageId, versionId));
    }

    @PostMapping("/api/v1/admin/portal-code-packages/{packageId}/publish/{versionId}")
    @PreAuthorize("@ss.hasPermi('portal-code:publish')")
    public ApiResult<Map<String, Object>> publish(@PathVariable Long packageId, @PathVariable Long versionId) {
        return ApiResult.success(service.publish(packageId, versionId));
    }

    @PostMapping("/api/v1/admin/portal-code-packages/{packageId}/revoke/{versionId}")
    @PreAuthorize("@ss.hasPermi('portal-code:revoke')")
    public ApiResult<Void> revoke(@PathVariable Long packageId, @PathVariable Long versionId) {
        service.revoke(packageId, versionId);
        return ApiResult.success();
    }

    @GetMapping("/portal-sandbox/{packageKey}/{version}/{*filePath}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> publishedFile(@PathVariable String packageKey,
                                                @PathVariable String version,
                                                @PathVariable String filePath) {
        PortalCodePackageService.StaticResource resource =
            service.publishedFile(packageKey, version, filePath);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resource.mimeType()))
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
            .header("Content-Security-Policy",
                "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self'; connect-src 'none'; frame-ancestors "
                    + frameAncestors())
            .header("Cross-Origin-Resource-Policy", "cross-origin")
            .header("X-Content-Type-Options", "nosniff")
            .body(resource.content());
    }

    private String frameAncestors() {
        if (!StringUtils.hasText(portalAppOrigin) || "'self'".equals(portalAppOrigin)) return "'self'";
        try {
            URI value = URI.create(portalAppOrigin);
            boolean localHttp = "http".equalsIgnoreCase(value.getScheme())
                && ("localhost".equalsIgnoreCase(value.getHost()) || "127.0.0.1".equals(value.getHost()));
            if (!"https".equalsIgnoreCase(value.getScheme()) && !localHttp) return "'self'";
            if (value.getHost() == null || value.getUserInfo() != null || value.getQuery() != null
                || value.getFragment() != null) return "'self'";
            return "'self' " + value.getScheme() + "://" + value.getAuthority();
        } catch (IllegalArgumentException ignored) {
            return "'self'";
        }
    }
}
