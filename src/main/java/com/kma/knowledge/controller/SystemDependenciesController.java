package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.health.KnowledgeHealthIndicator;
import com.kma.knowledge.health.ModelDependenciesHealthIndicator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/dependencies")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class SystemDependenciesController {
    private final KnowledgeHealthIndicator knowledgeHealthIndicator;
    private final ModelDependenciesHealthIndicator modelDependenciesHealthIndicator;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('dashboard:read')")
    public ApiResult<Map<String, Object>> dependencies() {
        Health core = knowledgeHealthIndicator.health();
        Health models = modelDependenciesHealthIndicator.health();
        return ApiResult.success(Map.of(
            "core", Map.of("status", core.getStatus().getCode(), "details", core.getDetails()),
            "models", Map.of("status", models.getStatus().getCode(), "details", models.getDetails())
        ));
    }
}
