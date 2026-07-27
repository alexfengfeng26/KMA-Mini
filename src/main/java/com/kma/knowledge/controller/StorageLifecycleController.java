package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.common.result.PageResult;
import com.kma.knowledge.storage.StorageLifecycleService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1/admin/storage")
@RequiredArgsConstructor
public class StorageLifecycleController {
    private final StorageLifecycleService service;

    @GetMapping("/objects")
    @PreAuthorize("@ss.hasPermi('storage:read')")
    public ApiResult<List<Map<String, Object>>> objects(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.success(service.list(status, limit));
    }

    @GetMapping("/objects/page")
    @PreAuthorize("@ss.hasPermi('storage:read')")
    public ApiResult<PageResult<Map<String, Object>>> objectPage(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "1") @Min(1) int pageNum,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(defaultValue = "updateTime") String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder) {
        return ApiResult.success(service.page(status, keyword, pageNum, pageSize, sortBy, sortOrder));
    }

    @PostMapping("/reconcile")
    @PreAuthorize("@ss.hasPermi('storage:reconcile')")
    public ApiResult<Map<String, Object>> reconcile() {
        return ApiResult.success(service.reconcileNow());
    }

    @PostMapping("/cleanup")
    @PreAuthorize("@ss.hasPermi('storage:cleanup')")
    public ApiResult<Void> cleanup() {
        service.cleanupNow();
        return ApiResult.success();
    }
}
