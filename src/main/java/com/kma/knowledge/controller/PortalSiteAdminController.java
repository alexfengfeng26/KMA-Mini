package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.PortalConfigDraftRequest;
import com.kma.knowledge.dto.PortalDesignCapabilityResponse;
import com.kma.knowledge.dto.PortalDesignProposalRequest;
import com.kma.knowledge.dto.PortalDesignProposalResponse;
import com.kma.knowledge.dto.PortalSiteCreateRequest;
import com.kma.knowledge.dto.PortalSiteUpdateRequest;
import com.kma.knowledge.dto.PortalVersionActionRequest;
import com.kma.knowledge.dto.PortalContentQuery;
import com.kma.knowledge.dto.PartyContentView;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.QAResult;
import com.kma.knowledge.service.PortalSiteService;
import com.kma.knowledge.service.PortalDesignService;
import com.kma.knowledge.service.KnowledgeQAService;
import com.kma.knowledge.service.KnowledgeStreamQAService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/portal-sites")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalSiteAdminController {
    private final PortalSiteService service;
    private final PortalDesignService designService;
    private final KnowledgeQAService qaService;
    private final KnowledgeStreamQAService streamQAService;

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

    @GetMapping("/{siteKey}/versions/{versionId}/preview/bootstrap")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<Map<String, Object>> previewBootstrap(@PathVariable String siteKey, @PathVariable Long versionId,
                                                            @RequestParam(defaultValue = "home") String page) {
        return ApiResult.success(service.previewBootstrap(siteKey, versionId, page));
    }

    @GetMapping("/{siteKey}/versions/{versionId}/preview/contents")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<Map<String, Object>> previewContents(@PathVariable String siteKey, @PathVariable Long versionId,
        @ParameterObject @Valid PortalContentQuery query) {
        return ApiResult.success(service.previewContents(siteKey, versionId, query));
    }

    @GetMapping("/{siteKey}/versions/{versionId}/preview/contents/{contentId}")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<PartyContentView> previewContent(@PathVariable String siteKey, @PathVariable Long versionId,
        @PathVariable Long contentId, @RequestParam(required = false) String location) {
        return ApiResult.success(service.previewContent(siteKey, versionId, contentId, location));
    }

    @PostMapping("/{siteKey}/versions/{versionId}/preview/ask")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<QAResult> previewAsk(@PathVariable String siteKey, @PathVariable Long versionId,
                                          @Valid @RequestBody QARequest request) {
        service.securePreviewQa(siteKey, versionId, request);
        return ApiResult.success(qaService.answer(request));
    }

    @PostMapping("/{siteKey}/versions/{versionId}/preview/ask/stream")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public SseEmitter previewAskStream(@PathVariable String siteKey, @PathVariable Long versionId,
                                       @Valid @RequestBody QARequest request) {
        service.securePreviewQa(siteKey, versionId, request);
        SseEmitter emitter = new SseEmitter(120_000L);
        streamQAService.streamAnswer(request, emitter);
        return emitter;
    }

    @GetMapping("/{siteKey}/design-capability")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<PortalDesignCapabilityResponse> designCapability(@PathVariable String siteKey) {
        service.getSite(siteKey);
        return ApiResult.success(designService.capability());
    }

    @PostMapping("/{siteKey}/design-proposals")
    @PreAuthorize("@ss.hasPermi('portal-site:update') and @ss.hasPermi('portal-page:edit')")
    public ApiResult<PortalDesignProposalResponse> designProposal(
        @PathVariable String siteKey,
        @Valid @RequestBody PortalDesignProposalRequest request
    ) {
        return ApiResult.success(designService.propose(siteKey, request));
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
