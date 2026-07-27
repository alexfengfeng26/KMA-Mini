package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.PortalEventRequest;
import com.kma.knowledge.service.PortalAnalyticsService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalAnalyticsController {
    private final PortalAnalyticsService service;

    @PostMapping("/portal-sites/{siteKey}/events")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Void> event(@PathVariable String siteKey,
                                 @Valid @RequestBody PortalEventRequest request) {
        service.record(siteKey, request);
        return ApiResult.success();
    }

    @GetMapping("/admin/portal-sites/{siteKey}/analytics")
    @PreAuthorize("@ss.hasPermi('portal-analytics:read')")
    public ApiResult<Map<String, Object>> summary(@PathVariable String siteKey,
                                                  @RequestParam(defaultValue = "30")
                                                  @Min(1) @Max(90) int days) {
        return ApiResult.success(service.summary(siteKey, days));
    }
}
