package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.knowledge.dto.DocIngestFileRequest;
import com.kma.knowledge.dto.DocIngestResult;
import com.kma.knowledge.dto.DocIngestTextRequest;
import com.kma.knowledge.dto.DocQueryRequest;
import com.kma.knowledge.dto.DocVO;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.enums.IngestionStatus;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.service.KnowledgeIngestionService;
import com.kma.knowledge.service.KnowledgeIngestionJobService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import com.kma.knowledge.storage.UploadTypeValidator;
import com.kma.knowledge.storage.KnowledgeStorage;
import com.kma.knowledge.storage.StorageLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 知识库摄入服务实现
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeIngestionJobService ingestionJobService;
    private final KnowledgeSpaceAclService aclService;
    private final UploadTypeValidator uploadTypeValidator;
    private final KnowledgeStorage knowledgeStorage;
    private final StorageLifecycleService storageLifecycleService;

    @Autowired
    public KnowledgeIngestionServiceImpl(KnowledgeDocMapper docMapper, KnowledgeChunkMapper chunkMapper,
                                         KnowledgeSpaceMapper spaceMapper, KnowledgeIngestionJobService ingestionJobService,
                                         KnowledgeSpaceAclService aclService, UploadTypeValidator uploadTypeValidator,
                                         KnowledgeStorage knowledgeStorage,
                                         StorageLifecycleService storageLifecycleService) {
        this.docMapper = docMapper; this.chunkMapper = chunkMapper; this.spaceMapper = spaceMapper;
        this.ingestionJobService = ingestionJobService; this.aclService = aclService;
        this.uploadTypeValidator = uploadTypeValidator; this.knowledgeStorage = knowledgeStorage;
        this.storageLifecycleService = storageLifecycleService;
    }

    public KnowledgeIngestionServiceImpl(KnowledgeDocMapper docMapper, KnowledgeChunkMapper chunkMapper,
                                         KnowledgeSpaceMapper spaceMapper, KnowledgeIngestionJobService ingestionJobService,
                                         KnowledgeSpaceAclService aclService, UploadTypeValidator uploadTypeValidator,
                                         KnowledgeStorage knowledgeStorage) {
        this(docMapper, chunkMapper, spaceMapper, ingestionJobService, aclService, uploadTypeValidator,
            knowledgeStorage, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public DocIngestResult ingestText(DocIngestTextRequest request) {
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(request.getSpaceCode());
        if (space == null) {
            throw new KmaException("知识空间不存在: " + request.getSpaceCode());
        }
        aclService.assertIngestAccess(request.getSpaceCode());
        return ingestText(request, space);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public DocIngestResult ingestTextAsSystem(DocIngestTextRequest request) {
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(request.getSpaceCode());
        if (space == null) {
            throw new KmaException("知识空间不存在: " + request.getSpaceCode());
        }
        return ingestText(request, space);
    }

    private DocIngestResult ingestText(DocIngestTextRequest request, KnowledgeSpace space) {

        // 幂等和乱序保护：同版本直接返回，旧版本拒绝，仅更高版本替换。
        DocIngestResult existing = prepareExternalRef(
            space.getSpaceId(), request.getExternalRef(), request.getSourceVersion());
        if (existing != null) {
            return existing;
        }

        long storageSize = request.getContent().getBytes(StandardCharsets.UTF_8).length;
        String storageChecksum = sha256(request.getContent().getBytes(StandardCharsets.UTF_8));


        // 文本也落盘，便于重索引
        String storagePath = saveTextToFile(space.getSpaceCode(),
            request.getTitle(), request.getContent());
        registerFileCleanupOnRollback(storagePath);
        Long storageObjectId = storageLifecycleService == null ? null
            : storageLifecycleService.registerStored(storagePath, storageSize, storageChecksum);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setSpaceId(space.getSpaceId());
        doc.setTitle(request.getTitle());
        doc.setSourceTag(request.getSourceTag());
        doc.setExternalRef(request.getExternalRef());
        doc.setSourceVersion(normalizeVersion(request.getSourceVersion()));
        doc.setIsActive(!StringUtils.hasText(request.getExternalRef()));
        doc.setSupersedesDocId(findActiveDocId(space.getSpaceId(), request.getExternalRef()));
        doc.setMimeType("text/plain");
        doc.setStoragePath(storagePath);
        doc.setStorageSizeBytes(storageSize);
        doc.setStorageObjectId(storageObjectId);
        doc.setContentHash(SecureUtil.md5(request.getContent()));
        doc.setParseStatus(IngestionStatus.PENDING.getCode());
        doc.setChunkCount(0);
        doc.setMeta(request.getMeta());
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        docMapper.insert(doc);
        if (storageLifecycleService != null) storageLifecycleService.bindDocument(storageObjectId, doc.getDocId());

        ingestionJobService.enqueue(doc.getDocId(), "ingest");
        return toResult(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public DocIngestResult ingestFile(DocIngestFileRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new KmaException("上传文件不能为空");
        }
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(request.getSpaceCode());
        if (space == null) {
            throw new KmaException("知识空间不存在: " + request.getSpaceCode());
        }
        aclService.assertIngestAccess(request.getSpaceCode());

        // 幂等和乱序保护：同版本直接返回，旧版本拒绝，仅更高版本替换。
        DocIngestResult existing = prepareExternalRef(
            space.getSpaceId(), request.getExternalRef(), request.getSourceVersion());
        if (existing != null) {
            return existing;
        }

        long storageSize = file.getSize();
        String storageChecksum = computeSha256(file);


        String originalFilename = StrUtil.blankToDefault(file.getOriginalFilename(), "unknown");
        String canonicalMimeType = uploadTypeValidator.validate(file, originalFilename);
        String storagePath = saveMultipartFile(space.getSpaceCode(), originalFilename, file);
        registerFileCleanupOnRollback(storagePath);
        Long storageObjectId = storageLifecycleService == null ? null
            : storageLifecycleService.registerStored(storagePath, storageSize, storageChecksum);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setSpaceId(space.getSpaceId());
        doc.setTitle(FileUtil.mainName(originalFilename));
        doc.setSourceTag(request.getSourceTag());
        doc.setExternalRef(request.getExternalRef());
        doc.setSourceVersion(normalizeVersion(request.getSourceVersion()));
        doc.setIsActive(!StringUtils.hasText(request.getExternalRef()));
        doc.setSupersedesDocId(findActiveDocId(space.getSpaceId(), request.getExternalRef()));
        doc.setMimeType(canonicalMimeType);
        doc.setStoragePath(storagePath);
        doc.setStorageSizeBytes(storageSize);
        doc.setStorageObjectId(storageObjectId);
        doc.setContentHash(computeHash(file));
        doc.setParseStatus(IngestionStatus.PENDING.getCode());
        doc.setChunkCount(0);
        doc.setMeta(request.getMeta());
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        docMapper.insert(doc);
        if (storageLifecycleService != null) storageLifecycleService.bindDocument(storageObjectId, doc.getDocId());

        ingestionJobService.enqueue(doc.getDocId(), "ingest");
        return toResult(doc);
    }

    @Override
    public DocIngestResult getStatus(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new KmaException("文档不存在");
        }
        KnowledgeSpace space = spaceMapper.selectById(doc.getSpaceId());
        if (space == null) throw new KmaException(404, "知识空间不存在");
        aclService.assertReadAccess(space.getSpaceCode());
        return toResult(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void reindex(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new KmaException("文档不存在");
        }
        KnowledgeSpace space = spaceMapper.selectById(doc.getSpaceId());
        if (space != null) {
            aclService.assertIngestAccess(space.getSpaceCode());
        }
        doc.setParseStatus(IngestionStatus.PENDING.getCode());
        doc.setChunkCount(0);
        doc.setErrorMessage(null);
        doc.setUpdateTime(LocalDateTime.now());
        docMapper.updateById(doc);
        ingestionJobService.enqueue(docId, "reindex");
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void delete(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new KmaException("文档不存在");
        }
        KnowledgeSpace space = spaceMapper.selectById(doc.getSpaceId());
        if (space != null) {
            aclService.assertAdminAccess(space.getSpaceCode());
        }
        chunkMapper.deleteByDocId(docId);
        docMapper.deleteById(docId);
        if (storageLifecycleService != null) storageLifecycleService.markOrphanIfUnreferenced(doc.getStorageObjectId());
    }

    @Override
    public Page<DocVO> page(DocQueryRequest request) {
        Page<KnowledgeDoc> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(request.getSpaceCode())) {
            aclService.assertReadAccess(request.getSpaceCode());
            KnowledgeSpace space = spaceMapper.selectBySpaceCode(request.getSpaceCode());
            if (space != null) {
                wrapper.eq(KnowledgeDoc::getSpaceId, space.getSpaceId());
            } else {
                // 空间编码不存在时返回空结果
                wrapper.eq(KnowledgeDoc::getSpaceId, -1L);
            }
        } else {
            // 未指定空间时按 ACL 过滤可读空间
            Set<Long> readableSpaceIds = aclService.getReadableSpaceIds();
            if (readableSpaceIds == null) {
                // 超级管理员不过滤
            } else if (readableSpaceIds.isEmpty()) {
                wrapper.eq(KnowledgeDoc::getSpaceId, -1L);
            } else {
                wrapper.in(KnowledgeDoc::getSpaceId, readableSpaceIds);
            }
        }
        if (StrUtil.isNotBlank(request.getTitle())) {
            wrapper.like(KnowledgeDoc::getTitle, request.getTitle());
        }
        if (StrUtil.isNotBlank(request.getParseStatus())) {
            wrapper.eq(KnowledgeDoc::getParseStatus, request.getParseStatus());
        }
        wrapper.orderByDesc(KnowledgeDoc::getCreateTime);

        Page<KnowledgeDoc> ApiResult = docMapper.selectPage(page, wrapper);

        // 批量查询空间编码
        List<Long> spaceIds = ApiResult.getRecords().stream()
            .map(KnowledgeDoc::getSpaceId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> spaceCodeMap = spaceIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : spaceMapper.selectByIds(spaceIds).stream()
                .collect(Collectors.toMap(KnowledgeSpace::getSpaceId, KnowledgeSpace::getSpaceCode));

        List<DocVO> records = ApiResult.getRecords().stream()
            .map(doc -> toVO(doc, spaceCodeMap.getOrDefault(doc.getSpaceId(), "")))
            .collect(Collectors.toList());

        Page<DocVO> voPage = new Page<>(ApiResult.getCurrent(), ApiResult.getSize(), ApiResult.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public List<DocVO> listVersions(Long docId) {
        KnowledgeDoc selected = docMapper.selectById(docId);
        if (selected == null) {
            throw new KmaException(404, "文档不存在");
        }
        KnowledgeSpace space = spaceMapper.selectById(selected.getSpaceId());
        if (space == null) {
            throw new KmaException(404, "知识空间不存在");
        }
        aclService.assertReadAccess(space.getSpaceCode());
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<KnowledgeDoc>()
            .eq(KnowledgeDoc::getSpaceId, selected.getSpaceId());
        if (StringUtils.hasText(selected.getExternalRef())) {
            wrapper.eq(KnowledgeDoc::getExternalRef, selected.getExternalRef());
        } else {
            wrapper.eq(KnowledgeDoc::getDocId, selected.getDocId());
        }
        wrapper.orderByDesc(KnowledgeDoc::getSourceVersion).orderByDesc(KnowledgeDoc::getCreateTime);
        return docMapper.selectList(wrapper).stream().map(doc -> toVO(doc, space.getSpaceCode())).toList();
    }

    private DocVO toVO(KnowledgeDoc doc, String spaceCode) {
        DocVO vo = new DocVO();
        vo.setDocId(doc.getDocId());
        vo.setSpaceId(doc.getSpaceId());
        vo.setSpaceCode(spaceCode);
        vo.setTitle(doc.getTitle());
        vo.setSourceTag(doc.getSourceTag());
        vo.setExternalRef(doc.getExternalRef());
        vo.setSourceVersion(doc.getSourceVersion());
        vo.setIsActive(doc.getIsActive());
        vo.setSupersedesDocId(doc.getSupersedesDocId());
        vo.setActivatedAt(doc.getActivatedAt());
        vo.setMimeType(doc.getMimeType());
        vo.setContentHash(doc.getContentHash());
        vo.setStorageObjectId(doc.getStorageObjectId());
        vo.setStorageSizeBytes(doc.getStorageSizeBytes());
        vo.setParseStatus(doc.getParseStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setErrorMessage(doc.getErrorMessage());
        vo.setCreateTime(doc.getCreateTime());
        vo.setUpdateTime(doc.getUpdateTime());
        return vo;
    }

    private String saveTextToFile(String spaceCode, String title, String content) {
        String fileName = FileUtil.mainName(title) + ".txt";
        try {
            return knowledgeStorage.store(spaceCode, fileName,
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            throw new KmaException("文本保存失败", ex);
        }
    }

    private String saveMultipartFile(String spaceCode, String originalFilename, MultipartFile file) {
        try (var input = file.getInputStream()) {
            return knowledgeStorage.store(spaceCode, originalFilename, input);
        } catch (IOException e) {
            throw new KmaException("文件保存失败: " + originalFilename, e);
        }
    }

    private String computeHash(MultipartFile file) {
        try (var input = file.getInputStream()) {
            return SecureUtil.md5(input);
        } catch (IOException ex) {
            throw new KmaException("无法计算上传文件摘要", ex);
        }
    }

    private String computeSha256(MultipartFile file) {
        try (var input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new KmaException("无法计算上传文件 SHA-256 摘要", ex);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算 SHA-256", ex);
        }
    }

    private DocIngestResult prepareExternalRef(Long spaceId, String externalRef, Long sourceVersion) {
        if (!StringUtils.hasText(externalRef) || spaceId == null) {
            return null;
        }
        List<KnowledgeDoc> existing = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getSpaceId, spaceId)
                .eq(KnowledgeDoc::getExternalRef, externalRef)
                .orderByDesc(KnowledgeDoc::getSourceVersion));
        long incomingVersion = normalizeVersion(sourceVersion);
        if (!existing.isEmpty()) {
            KnowledgeDoc newest = existing.get(0);
            long currentVersion = normalizeVersion(newest.getSourceVersion());
            if (incomingVersion < currentVersion) {
                throw new KmaException("拒绝乱序旧版本: externalRef=" + externalRef
                    + ", incoming=" + incomingVersion + ", current=" + currentVersion);
            }
            if (incomingVersion == currentVersion) {
                return toResult(newest);
            }
        }
        return null;
    }

    private Long findActiveDocId(Long spaceId, String externalRef) {
        if (spaceId == null || !StringUtils.hasText(externalRef)) {
            return null;
        }
        KnowledgeDoc active = docMapper.selectOne(new LambdaQueryWrapper<KnowledgeDoc>()
            .eq(KnowledgeDoc::getSpaceId, spaceId)
            .eq(KnowledgeDoc::getExternalRef, externalRef)
            .eq(KnowledgeDoc::getIsActive, true)
            .last("LIMIT 1"));
        return active == null ? null : active.getDocId();
    }

    private void registerFileCleanupOnRollback(String storagePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        knowledgeStorage.delete(storagePath);
                    } catch (Exception ex) {
                        log.warn("事务回滚后清理文件失败: {}", storagePath, ex);
                    }
                }
            }
        });
    }

    private long normalizeVersion(Long sourceVersion) {
        return sourceVersion == null || sourceVersion < 1 ? 1L : sourceVersion;
    }

    private DocIngestResult toResult(KnowledgeDoc doc) {
        DocIngestResult ApiResult = new DocIngestResult();
        ApiResult.setDocId(doc.getDocId());
        ApiResult.setTitle(doc.getTitle());
        ApiResult.setParseStatus(doc.getParseStatus());
        ApiResult.setChunkCount(doc.getChunkCount());
        ApiResult.setErrorMessage(doc.getErrorMessage());
        ApiResult.setCreateTime(doc.getCreateTime());
        return ApiResult;
    }
}



