package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.service.EmbeddingRebuildService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/embedding-rebuilds")
@RequiredArgsConstructor
public class EmbeddingRebuildController {
    private final EmbeddingRebuildService service;

    @PostMapping
    @PreAuthorize("@ss.hasPermi('embedding:rebuild')")
    public ApiResult<Long> start(@RequestParam Long datasetId,
        @RequestParam @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,64}") String targetProfileCode) {
        return ApiResult.success(service.start(datasetId, targetProfileCode));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('dataset:read')")
    public ApiResult<List<Map<String, Object>>> list(@RequestParam Long datasetId) {
        return ApiResult.success(service.list(datasetId));
    }

    @PostMapping("/{jobId}/activate")
    @PreAuthorize("@ss.hasPermi('embedding:activate')")
    public ApiResult<Map<String, Object>> activate(@PathVariable Long jobId) {
        return ApiResult.success(service.activate(jobId));
    }
}
