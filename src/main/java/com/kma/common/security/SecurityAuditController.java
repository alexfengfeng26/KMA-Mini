package com.kma.common.security;

import com.kma.common.result.ApiResult;
import com.kma.common.result.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springdoc.core.annotations.ParameterObject;

import java.util.List;
import java.util.Map;
import com.kma.common.security.dto.SecurityAuditQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;

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

    @GetMapping("/governance")
    public ApiResult<PageResult<Map<String, Object>>> governance(@ParameterObject @Valid SecurityAuditQuery query) {
        return ApiResult.success(service.governancePage(query));
    }

    @GetMapping("/governance/summary")
    public ApiResult<Map<String, Object>> governanceSummary(@RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return ApiResult.success(service.governanceSummary(days));
    }

    @GetMapping(value = "/governance/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportGovernance(@ParameterObject @Valid SecurityAuditQuery query) {
        query.setPageNum(1); query.setPageSize(100);
        StringBuilder csv = new StringBuilder("审计ID,时间,操作者,事件,动作,资源,级别,TraceId\\n");
        for (Map<String, Object> row : service.governancePage(query).list()) {
            csv.append(value(row.get("auditId"))).append(',').append(value(row.get("createTime"))).append(',')
                .append(value(row.get("username"))).append(',').append(value(row.get("eventType"))).append(',')
                .append(value(row.get("action"))).append(',').append(value(row.get("resource"))).append(',')
                .append(value(row.get("severity"))).append(',').append(value(row.get("traceId"))).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=governance-audit.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    private String value(Object value) {
        String text = value == null ? "" : String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }
}
