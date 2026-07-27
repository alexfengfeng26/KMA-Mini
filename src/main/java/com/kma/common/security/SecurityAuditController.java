package com.kma.common.security;

import com.kma.common.result.ApiResult;
import com.kma.common.result.PageResult;
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
@RequestMapping("/api/v1/security-audits")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermi('audit:security:read')")
public class SecurityAuditController {
    private final SecurityAuditService service;
    @GetMapping public ApiResult<List<Map<String, Object>>> list(@RequestParam(defaultValue = "100") int limit) {
        return ApiResult.success(service.list(limit));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
        @RequestParam(defaultValue = "1") @Min(1) int pageNum,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "createTime") String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder) {
        return ApiResult.success(service.page(pageNum, pageSize, keyword, sortBy, sortOrder));
    }
}
