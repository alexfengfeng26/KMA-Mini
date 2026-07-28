package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.GovernancePolicyRequest;
import com.kma.knowledge.service.GovernanceInsightsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/governance")
@RequiredArgsConstructor
public class GovernanceInsightsController {
    private final GovernanceInsightsService service;

    @GetMapping("/policy") @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String, Object>> policy() { return ApiResult.success(service.policy()); }

    @PutMapping("/policy") @PreAuthorize("@ss.hasPermi('content:publish')")
    public ApiResult<Map<String, Object>> updatePolicy(@Valid @RequestBody GovernancePolicyRequest request) {
        return ApiResult.success(service.updatePolicy(request));
    }

    @GetMapping("/insights") @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String, Object>> insights() { return ApiResult.success(service.insights()); }

    @GetMapping("/contents/{contentId}/impact") @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String, Object>> impact(@PathVariable Long contentId) { return ApiResult.success(service.contentImpact(contentId)); }
}
