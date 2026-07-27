package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.service.AclPrincipalValidator;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/admin/access/principals")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class AccessPrincipalController {
    private final AclPrincipalValidator validator;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('space:acl:manage')")
    public ApiResult<List<Map<String, Object>>> list(
        @RequestParam @Pattern(regexp = "user|role|org") String type,
        @RequestParam(defaultValue = "") String keyword) {
        return ApiResult.success(validator.list(type, keyword));
    }
}
