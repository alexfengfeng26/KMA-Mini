package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.DatasetCreateRequest;
import com.kma.knowledge.dto.DatasetQueryRequest;
import com.kma.knowledge.dto.DatasetUpdateRequest;
import com.kma.knowledge.dto.DatasetVO;
import com.kma.knowledge.service.KnowledgeDatasetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

/**
 * 数据集管理接口
 *
 * @author party
 * @date 2026/06/30
 */
@Tag(name = "KnowledgeDataset", description = "KnowledgeDataset 接口")
@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeDatasetController {

    private final KnowledgeDatasetService datasetService;

    @Operation(summary = "create")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('dataset:create')")
    public ApiResult<Long> create(@Valid @RequestBody DatasetCreateRequest request) {
        return ApiResult.success(datasetService.create(request));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermi('dataset:update')")
    public ApiResult<Void> update(@Valid @RequestBody DatasetUpdateRequest request) {
        datasetService.update(request);
        return ApiResult.success();
    }

    @Operation(summary = "delete")
    @DeleteMapping("/{datasetId}")
    @PreAuthorize("@ss.hasPermi('dataset:delete')")
    public ApiResult<Void> delete(@PathVariable Long datasetId) {
        datasetService.delete(datasetId);
        return ApiResult.success();
    }

    @GetMapping("/{datasetId}")
    @PreAuthorize("@ss.hasPermi('dataset:read')")
    public ApiResult<DatasetVO> getById(@PathVariable Long datasetId) {
        return ApiResult.success(datasetService.getById(datasetId));
    }

    @Operation(summary = "page")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermi('dataset:read')")
    public ApiResult<Map<String, Object>> page(@ParameterObject @Valid DatasetQueryRequest request) {
        Page<DatasetVO> pageResult = datasetService.page(request);
        return ApiResult.success(wrapPage(pageResult));
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('dataset:read')")
    public ApiResult<List<DatasetVO>> listActive() {
        return ApiResult.success(datasetService.listActive());
    }

    @Operation(summary = "changeStatus")
    @PutMapping("/{datasetId}/status")
    @PreAuthorize("@ss.hasPermi('dataset:status:update')")
    public ApiResult<Void> changeStatus(@PathVariable Long datasetId,
                                     @RequestParam String status) {
        datasetService.changeStatus(datasetId, status);
        return ApiResult.success();
    }

    private Map<String, Object> wrapPage(Page<DatasetVO> page) {
        Map<String, Object> data = new HashMap<>(4);
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return data;
    }
}



