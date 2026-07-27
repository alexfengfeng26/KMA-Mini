package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.RetrieveDebugResult;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.dto.RetrieveResult;
import com.kma.knowledge.service.KnowledgeRetrieveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 知识库检索接口
 *
 * @author party
 * @date 2026/06/30
 */
@Tag(name = "KnowledgeRetrieve", description = "KnowledgeRetrieve 接口")
@RestController
@RequestMapping("/api/v1/retrieval")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeRetrieveController {

    private final KnowledgeRetrieveService retrieveService;

    @Operation(summary = "retrieve")
    @PostMapping("/search")
    @PreAuthorize("@ss.hasPermi('retrieval:use')")
    public ApiResult<RetrieveResult> retrieve(@Valid @RequestBody RetrieveRequest request) {
        return ApiResult.success(retrieveService.retrieve(request));
    }

    @PostMapping("/debug")
    @PreAuthorize("@ss.hasPermi('retrieval:use')")
    public ApiResult<RetrieveDebugResult> debug(@Valid @RequestBody RetrieveRequest request) {
        return ApiResult.success(retrieveService.debug(request));
    }
}



