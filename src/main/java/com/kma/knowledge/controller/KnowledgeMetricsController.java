package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.service.KnowledgeMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 知识库 RAG 指标查询接口
 *
 * @author party
 * @date 2026/07/02
 */
@Tag(name = "KnowledgeMetrics", description = "KnowledgeMetrics 接口")
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeMetricsController {

    private final KnowledgeMetricsService metricsService;

    /**
     * 获取 RAG 运行大盘聚合指标
     *
     * @param spaceCode 可选，按空间过滤
     */
    @Operation(summary = "dashboard")
    @GetMapping("/dashboard")
    @PreAuthorize("@ss.hasPermi('dashboard:read')")
    public ApiResult<Map<String, Object>> dashboard(@RequestParam(required = false) String spaceCode) {
        return ApiResult.success(metricsService.getDashboardMetrics(spaceCode));
    }
}



