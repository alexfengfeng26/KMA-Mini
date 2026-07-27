package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.FeedTaskQueryRequest;
import com.kma.knowledge.dto.FeedTaskVO;
import com.kma.knowledge.service.KnowledgeFeedTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

/**
 * 知识库自动投喂任务管理接口
 *
 * @author party
 * @date 2026/07/02
 */
@Tag(name = "KnowledgeFeedTask", description = "KnowledgeFeedTask 接口")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeFeedTaskController {

    private final KnowledgeFeedTaskService feedTaskService;

    @Operation(summary = "page")
    @GetMapping
    @PreAuthorize("@ss.hasPermi('task:read')")
    public ApiResult<Map<String, Object>> page(@ParameterObject @Validated FeedTaskQueryRequest request) {
        Page<FeedTaskVO> pageResult = feedTaskService.page(request);
        return ApiResult.success(wrapPage(pageResult));
    }

    @GetMapping("/stats")
    @PreAuthorize("@ss.hasPermi('task:read')")
    public ApiResult<Map<String, Long>> stats() {
        return ApiResult.success(feedTaskService.stats());
    }

    @PostMapping("/{taskId}/retry")
    @PreAuthorize("@ss.hasPermi('task:retry')")
    public ApiResult<Void> retry(@PathVariable Long taskId) {
        feedTaskService.retry(taskId);
        return ApiResult.success();
    }

    private Map<String, Object> wrapPage(Page<FeedTaskVO> page) {
        Map<String, Object> data = new HashMap<>(4);
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return data;
    }
}



