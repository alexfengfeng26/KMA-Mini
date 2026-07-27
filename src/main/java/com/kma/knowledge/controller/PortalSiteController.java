package com.kma.knowledge.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.PartyContentView;
import com.kma.knowledge.dto.PortalContentQuery;
import com.kma.knowledge.dto.PortalDataBatchRequest;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.QAResult;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.service.KnowledgeQAService;
import com.kma.knowledge.service.KnowledgeStreamQAService;
import com.kma.knowledge.service.PortalSiteService;
import com.kma.knowledge.storage.KnowledgeStorage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portal-sites/{siteKey}")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalSiteController {
    private final PortalSiteService service;
    private final KnowledgeQAService qaService;
    private final KnowledgeStreamQAService streamQAService;
    private final KnowledgeStorage knowledgeStorage;

    @GetMapping("/bootstrap")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String, Object>> bootstrap(@PathVariable String siteKey,
                                                    @RequestParam(defaultValue = "home") String page) {
        return ApiResult.success(service.bootstrap(siteKey, page));
    }

    @GetMapping("/pages/{pageSlug}")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<JsonNode> page(@PathVariable String siteKey, @PathVariable String pageSlug) {
        return ApiResult.success(service.page(siteKey, pageSlug));
    }

    @GetMapping("/contents")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String, Object>> contents(@PathVariable String siteKey,
                                                   @ParameterObject @Valid PortalContentQuery query) {
        return ApiResult.success(service.contents(siteKey, query));
    }

    @PostMapping("/search")
    @PreAuthorize("@ss.hasPermi('content:read') and @ss.hasPermi('retrieval:use')")
    public ApiResult<Map<String, Object>> search(@PathVariable String siteKey,
                                                 @Valid @RequestBody PortalContentQuery query) {
        return ApiResult.success(service.contents(siteKey, query));
    }

    @PostMapping("/data/batch")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String, Object>> batchData(@PathVariable String siteKey,
                                                    @Valid @RequestBody PortalDataBatchRequest request) {
        return ApiResult.success(service.batchData(siteKey, request));
    }

    @GetMapping("/contents/{contentId}")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<PartyContentView> content(@PathVariable String siteKey,
                                               @PathVariable Long contentId,
                                               @RequestParam(required = false) String location) {
        return ApiResult.success(service.content(siteKey, contentId, location));
    }

    @GetMapping("/contents/{contentId}/source")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ResponseEntity<InputStreamResource> source(@PathVariable String siteKey,
                                                       @PathVariable Long contentId) throws Exception {
        KnowledgeDoc doc = service.document(siteKey, contentId);
        MediaType mediaType = doc.getMimeType() == null ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(doc.getMimeType());
        return ResponseEntity.ok().contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(doc.getTitle(), StandardCharsets.UTF_8).build().toString())
            .body(new InputStreamResource(knowledgeStorage.open(doc.getStoragePath())));
    }

    @PostMapping("/ask")
    @PreAuthorize("@ss.hasPermi('content:read') and @ss.hasPermi('qa:use')")
    public ApiResult<QAResult> ask(@PathVariable String siteKey, @Valid @RequestBody QARequest request) {
        service.secureQa(siteKey, request);
        return ApiResult.success(qaService.answer(request));
    }

    @PostMapping("/ask/stream")
    @PreAuthorize("@ss.hasPermi('content:read') and @ss.hasPermi('qa:use')")
    public SseEmitter askStream(@PathVariable String siteKey, @Valid @RequestBody QARequest request) {
        service.secureQa(siteKey, request);
        SseEmitter emitter = new SseEmitter(120_000L);
        streamQAService.streamAnswer(request, emitter);
        return emitter;
    }
}
