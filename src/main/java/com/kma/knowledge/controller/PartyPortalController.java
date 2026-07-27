package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.*;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.service.KnowledgeQAService;
import com.kma.knowledge.service.KnowledgeStreamQAService;
import com.kma.knowledge.service.PartyKnowledgeService;
import com.kma.knowledge.storage.KnowledgeStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Internal authenticated knowledge portal. */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PartyPortalController {
    private final PartyKnowledgeService partyKnowledgeService;
    private final KnowledgeQAService qaService;
    private final KnowledgeStreamQAService streamQAService;
    private final KnowledgeStorage knowledgeStorage;

    @GetMapping("/home")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String,Object>> home(){return ApiResult.success(partyKnowledgeService.home());}

    @GetMapping("/contents")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String,Object>> contents(@ParameterObject @Valid PortalContentQuery query){return ApiResult.success(partyKnowledgeService.portalPage(query));}

    @GetMapping("/contents/{contentId}")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<PartyContentView> content(@PathVariable Long contentId,@RequestParam(required=false) String location){return ApiResult.success(partyKnowledgeService.getPortalContent(contentId,location));}

    @GetMapping("/contents/{contentId}/source")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ResponseEntity<InputStreamResource> source(@PathVariable Long contentId) throws Exception {
        KnowledgeDoc doc=partyKnowledgeService.getPortalDocument(contentId);
        MediaType mediaType=doc.getMimeType()==null?MediaType.APPLICATION_OCTET_STREAM:MediaType.parseMediaType(doc.getMimeType());
        return ResponseEntity.ok().contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(doc.getTitle(), StandardCharsets.UTF_8).build().toString())
            .body(new InputStreamResource(knowledgeStorage.open(doc.getStoragePath())));
    }

    @GetMapping("/topics")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<List<Map<String,Object>>> topics(){return ApiResult.success(partyKnowledgeService.topics(true));}

    @PostMapping("/ask")
    @PreAuthorize("@ss.hasPermi('content:read') and @ss.hasPermi('qa:use')")
    public ApiResult<QAResult> ask(@Valid @RequestBody QARequest request){securePortalQa(request);return ApiResult.success(qaService.answer(request));}

    @PostMapping("/ask/stream")
    @PreAuthorize("@ss.hasPermi('content:read') and @ss.hasPermi('qa:use')")
    public SseEmitter askStream(@Valid @RequestBody QARequest request){securePortalQa(request);SseEmitter emitter=new SseEmitter(120_000L);streamQAService.streamAnswer(request,emitter);return emitter;}

    @GetMapping("/favorites")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<List<Map<String,Object>>> favorites(@RequestParam(defaultValue="100") @Min(1) @Max(100) int limit){return ApiResult.success(partyKnowledgeService.favorites(limit));}

    @PostMapping("/favorites")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Long> favorite(@Valid @RequestBody FavoriteRequest request){return ApiResult.success(partyKnowledgeService.addFavorite(request));}

    @DeleteMapping("/favorites/{favoriteId}")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Void> removeFavorite(@PathVariable Long favoriteId){partyKnowledgeService.removeFavorite(favoriteId);return ApiResult.success();}

    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<List<Map<String,Object>>> history(@RequestParam(defaultValue="100") @Min(1) @Max(100) int limit){return ApiResult.success(partyKnowledgeService.history(limit));}

    private void securePortalQa(QARequest request){
        request.setPortalOnly(true);
        request.setValidityStatuses(Boolean.TRUE.equals(request.getHistorical())
            ? List.of("effective","expired","repealed") : List.of("effective"));
    }
}
