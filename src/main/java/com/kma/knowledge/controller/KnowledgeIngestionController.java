package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.DocIngestFileRequest;
import com.kma.knowledge.dto.DocIngestResult;
import com.kma.knowledge.dto.DocIngestTextRequest;
import com.kma.knowledge.dto.DocQueryRequest;
import com.kma.knowledge.dto.DocVO;
import com.kma.knowledge.service.KnowledgeIngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;

/**
 * 知识库摄入接口
 *
 * @author party
 * @date 2026/06/30
 */
@Tag(name = "KnowledgeIngestion", description = "KnowledgeIngestion 接口")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeIngestionController {

    private final KnowledgeIngestionService ingestionService;

    @Operation(summary = "ingestText")
    @PostMapping("/text")
    @PreAuthorize("@ss.hasPermi('document:ingest')")
    public ApiResult<DocIngestResult> ingestText(
        @Size(max = 256) @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody DocIngestTextRequest request) {
        applyIdempotencyKey(request, idempotencyKey);
        return ApiResult.success(ingestionService.ingestText(request));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermi('document:ingest')")
    public ApiResult<DocIngestResult> ingestFile(
                                              @Size(max = 256) @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                              @ParameterObject @Valid DocIngestFileRequest request,
                                              @RequestPart("file") MultipartFile file) {
        if ((request.getExternalRef() == null || request.getExternalRef().isBlank())
            && idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.setExternalRef(idempotencyKey);
        }
        return ApiResult.success(ingestionService.ingestFile(request, file));
    }

    @Operation(summary = "getStatus")
    @GetMapping("/{docId}/status")
    @PreAuthorize("@ss.hasPermi('document:read')")
    public ApiResult<DocIngestResult> getStatus(@PathVariable Long docId) {
        return ApiResult.success(ingestionService.getStatus(docId));
    }

    @PostMapping("/{docId}/reindex")
    @PreAuthorize("@ss.hasPermi('document:reindex')")
    public ApiResult<Void> reindex(@PathVariable Long docId) {
        ingestionService.reindex(docId);
        return ApiResult.success();
    }

    @GetMapping("/{docId}/versions")
    @PreAuthorize("@ss.hasPermi('document:read')")
    public ApiResult<List<DocVO>> versions(@PathVariable Long docId) {
        return ApiResult.success(ingestionService.listVersions(docId));
    }

    @Operation(summary = "delete")
    @DeleteMapping("/{docId}")
    @PreAuthorize("@ss.hasPermi('document:delete')")
    public ApiResult<Void> delete(@PathVariable Long docId) {
        ingestionService.delete(docId);
        return ApiResult.success();
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermi('document:read')")
    public ApiResult<Map<String, Object>> page(@ParameterObject @Valid DocQueryRequest request) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DocVO> pageResult = ingestionService.page(request);
        return ApiResult.success(wrapPage(pageResult));
    }

    private Map<String, Object> wrapPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<DocVO> page) {
        Map<String, Object> data = new HashMap<>(4);
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return data;
    }

    private void applyIdempotencyKey(DocIngestTextRequest request, String idempotencyKey) {
        if ((request.getExternalRef() == null || request.getExternalRef().isBlank())
            && idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.setExternalRef(idempotencyKey);
        }
    }
}



