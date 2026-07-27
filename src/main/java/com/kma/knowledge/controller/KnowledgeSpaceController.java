package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.common.result.ApiResult;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.SpaceAclRequest;
import com.kma.knowledge.dto.SpaceCreateRequest;
import com.kma.knowledge.dto.SpaceQueryRequest;
import com.kma.knowledge.dto.SpaceUpdateRequest;
import com.kma.knowledge.dto.SpaceVO;
import com.kma.knowledge.service.KnowledgeDataGovernanceService;
import com.kma.knowledge.service.KnowledgeSpaceService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import com.kma.knowledge.service.AclPrincipalValidator;
import com.kma.knowledge.dto.SpaceAclView;
import com.kma.knowledge.entity.KnowledgeSpaceAcl;
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
import java.util.Map;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

/**
 * 知识空间管理接口
 *
 * @author party
 * @date 2026/06/30
 */
@Tag(name = "KnowledgeSpace", description = "KnowledgeSpace 接口")
@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeSpaceController {

    private final KnowledgeSpaceService spaceService;
    private final KnowledgeDataGovernanceService governanceService;
    private final KnowledgeSpaceAclService aclService;
    private final AclPrincipalValidator principalValidator;

    @Operation(summary = "create")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('space:create')")
    public ApiResult<Long> create(@Valid @RequestBody SpaceCreateRequest request) {
        return ApiResult.success(spaceService.create(request));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermi('space:update')")
    public ApiResult<Void> update(@Valid @RequestBody SpaceUpdateRequest request) {
        spaceService.update(request);
        return ApiResult.success();
    }

    @Operation(summary = "delete")
    @DeleteMapping("/{spaceId}")
    @PreAuthorize("@ss.hasPermi('space:delete')")
    public ApiResult<Void> delete(@PathVariable Long spaceId) {
        spaceService.delete(spaceId);
        return ApiResult.success();
    }

    @GetMapping("/id/{spaceId}")
    @PreAuthorize("@ss.hasPermi('space:read')")
    public ApiResult<SpaceVO> getById(@PathVariable Long spaceId) {
        return ApiResult.success(spaceService.getById(spaceId));
    }

    @Operation(summary = "getBySpaceCode")
    @GetMapping("/{spaceCode}")
    @PreAuthorize("@ss.hasPermi('space:read')")
    public ApiResult<SpaceVO> getBySpaceCode(@PathVariable String spaceCode) {
        return ApiResult.success(spaceService.getBySpaceCode(spaceCode));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermi('space:read')")
    public ApiResult<Map<String, Object>> page(@ParameterObject @Valid SpaceQueryRequest request) {
        Page<SpaceVO> pageResult = spaceService.page(request);
        return ApiResult.success(wrapPage(pageResult));
    }

    @Operation(summary = "changeStatus")
    @PutMapping("/{spaceId}/status")
    @PreAuthorize("@ss.hasPermi('space:update')")
    public ApiResult<Void> changeStatus(@PathVariable Long spaceId,
                                     @RequestParam String status) {
        spaceService.changeStatus(spaceId, status);
        return ApiResult.success();
    }

    @PostMapping("/{spaceCode}/acl")
    @PreAuthorize("@ss.hasPermi('space:acl:manage')")
    public ApiResult<Long> addAcl(@PathVariable String spaceCode,
                               @Valid @RequestBody SpaceAclRequest request) {
        SpaceVO space = spaceService.getBySpaceCode(spaceCode);
        if (!space.getSpaceId().equals(request.getSpaceId())) {
            throw new KmaException(400, "ACL 空间 ID 与路径空间不一致");
        }
        return ApiResult.success(spaceService.addAcl(request));
    }

    @GetMapping("/{spaceCode}/acl")
    @PreAuthorize("@ss.hasPermi('space:acl:manage')")
    public ApiResult<List<SpaceAclView>> listAcls(@PathVariable String spaceCode) {
        SpaceVO space = spaceService.getBySpaceCode(spaceCode);
        return ApiResult.success(spaceService.listAcls(space.getSpaceId()).stream()
            .map(principalValidator::toView).toList());
    }

    @Operation(summary = "removeAcl")
    @DeleteMapping("/{spaceCode}/acl/{aclId}")
    @PreAuthorize("@ss.hasPermi('space:acl:manage')")
    public ApiResult<Void> removeAcl(@PathVariable String spaceCode,
                                  @PathVariable Long aclId) {
        SpaceVO space = spaceService.getBySpaceCode(spaceCode);
        boolean belongsToSpace = spaceService.listAcls(space.getSpaceId()).stream()
            .anyMatch(acl -> aclId.equals(acl.getAclId()));
        if (!belongsToSpace) {
            throw new KmaException(404, "ACL 不属于指定空间");
        }
        spaceService.removeAcl(aclId);
        return ApiResult.success();
    }

    @PostMapping("/{spaceCode}/reindex")
    @PreAuthorize("@ss.hasPermi('space:reindex')")
    public ApiResult<Void> reindexSpace(@PathVariable String spaceCode) {
        aclService.assertAdminAccess(spaceCode);
        governanceService.reindexSpace(spaceCode);
        return ApiResult.success();
    }

    private Map<String, Object> wrapPage(Page<SpaceVO> page) {
        Map<String, Object> data = new HashMap<>(4);
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return data;
    }
}



