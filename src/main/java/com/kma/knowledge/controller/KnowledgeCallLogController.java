package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.CallLogQueryRequest;
import com.kma.knowledge.dto.KnowledgeCallLogVO;
import com.kma.knowledge.service.KnowledgeCallLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

/**
 * 知识库调用日志接口
 *
 * @author party
 * @date 2026/07/02
 */
@Tag(name = "KnowledgeCallLog", description = "KnowledgeCallLog 接口")
@RestController
@RequestMapping("/api/v1/call-logs")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeCallLogController {

    private final KnowledgeCallLogService callLogService;

    @Operation(summary = "page")
    @GetMapping
    @PreAuthorize("@ss.hasPermi('audit:call:read')")
    public ApiResult<Map<String, Object>> page(@ParameterObject @Valid CallLogQueryRequest request) {
        Page<KnowledgeCallLogVO> pageResult = callLogService.page(request);
        return ApiResult.success(wrapPage(pageResult));
    }

    @GetMapping("/{logId}")
    @PreAuthorize("@ss.hasPermi('audit:call:read')")
    public ApiResult<KnowledgeCallLogVO> getById(@PathVariable Long logId) {
        return ApiResult.success(callLogService.getById(logId));
    }

    private Map<String, Object> wrapPage(Page<KnowledgeCallLogVO> page) {
        Map<String, Object> data = new HashMap<>(4);
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return data;
    }
}



