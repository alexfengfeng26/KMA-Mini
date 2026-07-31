package com.kma.knowledge.controller;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.PortalConfigRequest;
import com.kma.knowledge.dto.TopicRequest;
import com.kma.knowledge.service.PartyKnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PartyTopicAdminController {
    private final PartyKnowledgeService service;

    @GetMapping("/topics")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<List<Map<String,Object>>> topics(){return ApiResult.success(service.topics(false));}

    @PostMapping("/topics")
    @PreAuthorize("@ss.hasPermi('topic:manage')")
    public ApiResult<Long> create(@Valid @RequestBody TopicRequest request){return ApiResult.success(service.saveTopic(null,request));}

    @PutMapping("/topics/{id}")
    @PreAuthorize("@ss.hasPermi('topic:manage')")
    public ApiResult<Long> update(@PathVariable Long id,@Valid @RequestBody TopicRequest request){return ApiResult.success(service.saveTopic(id,request));}

    @DeleteMapping("/topics/{id}")
    @PreAuthorize("@ss.hasPermi('topic:manage')")
    public ApiResult<Void> delete(@PathVariable Long id){service.deleteTopic(id);return ApiResult.success();}

    @PostMapping("/topics/reorder")
    @PreAuthorize("@ss.hasPermi('topic:manage')")
    public ApiResult<Void> reorder(@RequestBody List<Map<String,Object>> order){service.reorderTopics(order);return ApiResult.success();}

    @GetMapping("/portal-config")
    @PreAuthorize("@ss.hasPermi('content:read')")
    public ApiResult<Map<String,Object>> config(){return ApiResult.success(service.config());}

    @PutMapping("/portal-config")
    @PreAuthorize("@ss.hasPermi('portal:configure')")
    public ApiResult<Void> config(@Valid @RequestBody PortalConfigRequest request){service.updateConfig(request);return ApiResult.success();}
}
