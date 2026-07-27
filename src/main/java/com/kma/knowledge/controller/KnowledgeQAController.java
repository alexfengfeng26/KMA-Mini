package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.QAResult;
import com.kma.knowledge.service.KnowledgeQAService;
import com.kma.knowledge.service.KnowledgeStreamQAService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 知识库问答接口
 *
 * @author party
 * @date 2026/06/30
 */
@Tag(name = "KnowledgeQA", description = "KnowledgeQA 接口")
@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeQAController {

    private final KnowledgeQAService qaService;
    private final KnowledgeStreamQAService streamQAService;

    @Operation(summary = "answer")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('qa:use')")
    public ApiResult<QAResult> answer(@Valid @RequestBody QARequest request) {
        return ApiResult.success(qaService.answer(request));
    }

    @PostMapping("/stream")
    @PreAuthorize("@ss.hasPermi('qa:use')")
    public SseEmitter streamAnswer(@Valid @RequestBody QARequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        streamQAService.streamAnswer(request, emitter);
        return emitter;
    }
}



