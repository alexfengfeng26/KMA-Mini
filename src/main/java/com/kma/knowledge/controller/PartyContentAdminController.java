package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.*;
import com.kma.knowledge.service.PartyKnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Governance endpoints for draft, review and publication lifecycle. */
@RestController
@RequestMapping("/api/v1/admin/contents")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PartyContentAdminController {
    private final PartyKnowledgeService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String,Object>> page(@ParameterObject @Valid AdminContentQuery query){return ApiResult.success(service.adminPage(query));}

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<PartyContentView> get(@PathVariable Long id){return ApiResult.success(service.getAdminContent(id));}

    @PostMapping("/text")
    @PreAuthorize("@ss.hasPermi('content:create')")
    public ApiResult<PartyContentView> createText(@Valid @RequestBody PartyContentRequest request){return ApiResult.success(service.createText(request));}

    @PostMapping(value="/file",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermi('content:create')")
    public ApiResult<PartyContentView> createFile(@ParameterObject @Valid PartyContentFileRequest request,@RequestPart("file") MultipartFile file){return ApiResult.success(service.createFile(request,file));}

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('content:update')")
    public ApiResult<PartyContentView> update(@PathVariable Long id,@Valid @RequestBody PartyContentMetadataRequest request){return ApiResult.success(service.update(id,request));}

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.hasPermi('content:submit')")
    public ApiResult<Void> submit(@PathVariable Long id){service.submit(id);return ApiResult.success();}

    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPermi('content:review')")
    public ApiResult<Void> approve(@PathVariable Long id,@RequestBody(required=false) @Valid ReviewRequest request){service.approve(id,request);return ApiResult.success();}

    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermi('content:review')")
    public ApiResult<Void> reject(@PathVariable Long id,@RequestBody(required=false) @Valid ReviewRequest request){service.reject(id,request);return ApiResult.success();}

    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermi('content:publish')")
    public ApiResult<Void> publish(@PathVariable Long id){service.publish(id);return ApiResult.success();}

    @PostMapping("/{id}/offline")
    @PreAuthorize("@ss.hasPermi('content:publish')")
    public ApiResult<Void> offline(@PathVariable Long id,@RequestBody(required=false) @Valid ReviewRequest request){service.offline(id,request);return ApiResult.success();}

    @PostMapping("/{id}/restore")
    @PreAuthorize("@ss.hasPermi('content:publish')")
    public ApiResult<Void> restore(@PathVariable Long id){service.publish(id);return ApiResult.success();}
}
