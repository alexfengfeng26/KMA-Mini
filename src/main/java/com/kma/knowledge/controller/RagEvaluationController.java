package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.common.result.PageResult;
import com.kma.knowledge.dto.EvaluationCaseRequest;
import com.kma.knowledge.dto.EvaluationDatasetRequest;
import com.kma.knowledge.dto.EvaluationGateRequest;
import com.kma.knowledge.service.RagEvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class RagEvaluationController {
    private final RagEvaluationService service;

    @PostMapping("/datasets")
    @PreAuthorize("@ss.hasPermi('evaluation:create')")
    public ApiResult<Long> createDataset(@Valid @RequestBody EvaluationDatasetRequest request) {
        return ApiResult.success(service.createDataset(request));
    }

    @GetMapping("/datasets")
    @PreAuthorize("@ss.hasPermi('evaluation:read')")
    public ApiResult<List<Map<String, Object>>> datasets() {
        return ApiResult.success(service.listDatasets());
    }

    @PostMapping("/datasets/{datasetId}/cases")
    @PreAuthorize("@ss.hasPermi('evaluation:case:create')")
    public ApiResult<Long> addCase(@PathVariable Long datasetId, @Valid @RequestBody EvaluationCaseRequest request) {
        return ApiResult.success(service.addCase(datasetId, request));
    }

    @PostMapping("/datasets/{datasetId}/runs")
    @PreAuthorize("@ss.hasPermi('evaluation:run')")
    public ApiResult<Map<String, Object>> run(@PathVariable Long datasetId,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int topK) {
        return ApiResult.success(service.run(datasetId, topK));
    }

    @GetMapping("/datasets/{datasetId}/runs")
    @PreAuthorize("@ss.hasPermi('evaluation:read')")
    public ApiResult<List<Map<String, Object>>> runs(@PathVariable Long datasetId) {
        return ApiResult.success(service.listRuns(datasetId));
    }

    @GetMapping("/datasets/{datasetId}/runs/page")
    @PreAuthorize("@ss.hasPermi('evaluation:read')")
    public ApiResult<PageResult<Map<String, Object>>> runPage(
        @PathVariable Long datasetId,
        @RequestParam(defaultValue = "1") @Min(1) int pageNum,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "startTime") String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder) {
        return ApiResult.success(service.runPage(datasetId, pageNum, pageSize, keyword, sortBy, sortOrder));
    }

    @PutMapping("/datasets/{datasetId}/gate")
    @PreAuthorize("@ss.hasPermi('evaluation:gate:update')")
    public ApiResult<Void> configureGate(@PathVariable Long datasetId,
        @Valid @RequestBody EvaluationGateRequest request) {
        service.configureGate(datasetId, request);
        return ApiResult.success();
    }

    @GetMapping("/datasets/{datasetId}/gate")
    @PreAuthorize("@ss.hasPermi('evaluation:read')")
    public ApiResult<Map<String, Object>> gate(@PathVariable Long datasetId) {
        return ApiResult.success(service.getGate(datasetId));
    }

    @PostMapping("/runs/{runId}/assert-release")
    @PreAuthorize("@ss.hasPermi('evaluation:release:assert')")
    public ApiResult<Map<String, Object>> assertRelease(@PathVariable Long runId) {
        return ApiResult.success(service.assertReleaseReady(runId));
    }
}
