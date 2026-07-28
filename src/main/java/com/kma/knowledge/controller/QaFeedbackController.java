package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.QaFeedbackRequest;
import com.kma.knowledge.service.QaFeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QaFeedbackController {
    private final QaFeedbackService service;

    @PostMapping("/portal/qa-feedback")
    @PreAuthorize("@ss.hasPermi('content:read') and @ss.hasPermi('qa:use')")
    public ApiResult<Long> record(@Valid @RequestBody QaFeedbackRequest request) { return ApiResult.success(service.record(request)); }

    @GetMapping("/admin/qa-feedback")
    @PreAuthorize("@ss.hasPermi('evaluation:read')")
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return ApiResult.success(service.list(limit));
    }

    @PostMapping("/admin/qa-feedback/{feedbackId}/evaluation-case")
    @PreAuthorize("@ss.hasPermi('evaluation:case:create')")
    public ApiResult<Long> convert(@PathVariable Long feedbackId, @RequestParam Long datasetId) {
        return ApiResult.success(service.convertToEvaluationCase(feedbackId, datasetId));
    }
}
