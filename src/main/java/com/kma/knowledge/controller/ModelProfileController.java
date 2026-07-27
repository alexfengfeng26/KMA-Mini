package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.ModelProfileRequest;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.service.ModelProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/model-profiles")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class ModelProfileController {
    private final ModelProfileService modelProfileService;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('model:read')")
    public ApiResult<List<ModelProfile>> list(@RequestParam(required = false) String capability) {
        return ApiResult.success(modelProfileService.list(capability));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('model:create')")
    public ApiResult<ModelProfile> create(@Valid @RequestBody ModelProfileRequest request) {
        return ApiResult.success(modelProfileService.create(request));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermi('model:update')")
    public ApiResult<ModelProfile> update(@Valid @RequestBody ModelProfileRequest request) {
        return ApiResult.success(modelProfileService.update(request));
    }
}
