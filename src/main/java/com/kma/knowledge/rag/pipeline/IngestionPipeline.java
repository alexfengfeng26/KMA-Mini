package com.kma.knowledge.rag.pipeline;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.client.embedding.EmbeddingClient;
import com.kma.knowledge.client.embedding.EmbeddingClientFactory;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.entity.KnowledgeChunk;
import com.kma.knowledge.entity.KnowledgeChunkEmbedding;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.model.ResolvedModelProfile;
import com.kma.knowledge.enums.IngestionStatus;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeChunkEmbeddingMapper;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.service.ModelProfileResolver;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import com.kma.knowledge.rag.chunk.ChunkOptions;
import com.kma.knowledge.rag.chunk.ChunkStrategy;
import com.kma.knowledge.rag.chunk.ChunkStrategyFactory;
import com.kma.knowledge.rag.extract.DocumentExtractorRegistry;
import com.kma.knowledge.rag.extract.OcrRequiredException;
import com.kma.knowledge.rag.retrieve.LexicalQueryAnalyzer;
import com.kma.knowledge.storage.KnowledgeStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档摄入流水线
 * <p>
 * 异步执行：解析 → 分块 → 向量化 → 入库
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class IngestionPipeline {

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeChunkEmbeddingMapper chunkEmbeddingMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final DocumentExtractorRegistry extractorRegistry;
    private final ChunkStrategyFactory chunkStrategyFactory;
    private final EmbeddingClientFactory embeddingClientFactory;
    private final KnowledgeProperties properties;
    private final RagMetricsRecorder metricsRecorder;
    private final TransactionTemplate knowledgeTransactionTemplate;
    private final LexicalQueryAnalyzer lexicalQueryAnalyzer;
    private final KnowledgeStorage knowledgeStorage;
    private final KnowledgeDatasetMapper datasetMapper;
    private final ModelProfileResolver profileResolver;

    @Autowired
    public IngestionPipeline(KnowledgeDocMapper docMapper, KnowledgeChunkMapper chunkMapper,
                             KnowledgeChunkEmbeddingMapper chunkEmbeddingMapper, KnowledgeSpaceMapper spaceMapper,
                             DocumentExtractorRegistry extractorRegistry, ChunkStrategyFactory chunkStrategyFactory,
                             EmbeddingClientFactory embeddingClientFactory, KnowledgeProperties properties,
                             RagMetricsRecorder metricsRecorder, TransactionTemplate knowledgeTransactionTemplate,
                             LexicalQueryAnalyzer lexicalQueryAnalyzer, KnowledgeStorage knowledgeStorage,
                             KnowledgeDatasetMapper datasetMapper, ModelProfileResolver profileResolver) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.chunkEmbeddingMapper = chunkEmbeddingMapper;
        this.spaceMapper = spaceMapper;
        this.extractorRegistry = extractorRegistry;
        this.chunkStrategyFactory = chunkStrategyFactory;
        this.embeddingClientFactory = embeddingClientFactory;
        this.properties = properties;
        this.metricsRecorder = metricsRecorder;
        this.knowledgeTransactionTemplate = knowledgeTransactionTemplate;
        this.lexicalQueryAnalyzer = lexicalQueryAnalyzer;
        this.knowledgeStorage = knowledgeStorage;
        this.datasetMapper = datasetMapper;
        this.profileResolver = profileResolver;
    }

    /** Compatibility constructor for isolated pipeline tests and legacy embedders without dataset Profiles. */
    public IngestionPipeline(KnowledgeDocMapper docMapper, KnowledgeChunkMapper chunkMapper,
                             KnowledgeChunkEmbeddingMapper chunkEmbeddingMapper, KnowledgeSpaceMapper spaceMapper,
                             DocumentExtractorRegistry extractorRegistry, ChunkStrategyFactory chunkStrategyFactory,
                             EmbeddingClientFactory embeddingClientFactory, KnowledgeProperties properties,
                             RagMetricsRecorder metricsRecorder, TransactionTemplate knowledgeTransactionTemplate,
                             LexicalQueryAnalyzer lexicalQueryAnalyzer, KnowledgeStorage knowledgeStorage) {
        this(docMapper, chunkMapper, chunkEmbeddingMapper, spaceMapper, extractorRegistry, chunkStrategyFactory,
            embeddingClientFactory, properties, metricsRecorder, knowledgeTransactionTemplate, lexicalQueryAnalyzer,
            knowledgeStorage, null, null);
    }

    public void run(Long docId) {
        long pipelineStart = System.currentTimeMillis();
        log.info("开始摄入文档: docId={}", docId);
        updateStatus(docId, IngestionStatus.PARSING.getCode(), null, null);

        KnowledgeDoc doc = null;
        KnowledgeSpace space = null;
        try {
            doc = docMapper.selectById(docId);
            if (doc == null) {
                throw new KmaException("文档不存在: " + docId);
            }
            space = spaceMapper.selectById(doc.getSpaceId());
            if (space == null) {
                throw new KmaException("知识空间不存在: " + doc.getSpaceId());
            }

            // 1. 解析文档
            String text = parseDocument(doc);
            String contentHash = SecureUtil.md5(text);
            updateStatus(docId, IngestionStatus.CHUNKING.getCode(), null, contentHash);

            // 2. 分块
            List<String> chunks = splitText(text, space);
            log.info("文档分块完成: docId={}, chunks={}", docId, chunks.size());

            // 3. 向量化
            updateStatus(docId, IngestionStatus.EMBEDDING.getCode(), null, null);
            KnowledgeDataset dataset = space.getDatasetId() == null || datasetMapper == null
                ? null : datasetMapper.selectById(space.getDatasetId());
            String profileCode = dataset == null ? null : dataset.getEmbeddingProfileCode();
            ResolvedModelProfile profile = profileCode == null || profileCode.isBlank()
                ? null : profileResolver.resolve(profileCode, "embedding");
            EmbeddingClient client = profile == null
                ? embeddingClientFactory.get(space.getEmbeddingProvider())
                : embeddingClientFactory.getByProfile(profileCode);
            String embeddingModel = profile == null ? space.getEmbeddingModel() : profile.getModelName();
            int embeddingDimension = profile == null ? space.getEmbeddingDim() : profile.getDimension();
            List<float[]> embeddings = embedInBatches(chunks, client, embeddingModel);
            validateEmbeddings(embeddingDimension, chunks, embeddings);

            // 4. 在内存中构造待提交分块；外部模型调用结束后才开启短事务。
            List<KnowledgeChunk> chunkEntities = new ArrayList<>(chunks.size());
            int searchOffset = 0;
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(docId);
                chunk.setSpaceId(space.getSpaceId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setSearchText(lexicalQueryAnalyzer.analyzeDocument(chunks.get(i)));
                int charOffset = text.indexOf(chunks.get(i), Math.max(0, searchOffset));
                if (charOffset < 0) {
                    charOffset = searchOffset;
                }
                chunk.setCharOffset(charOffset);
                searchOffset = Math.max(charOffset + 1, searchOffset);
                chunk.setTokenCount(estimateTokenCount(chunks.get(i)));
                chunk.setSourceTag(doc.getSourceTag());
                float[] embedding = embeddings.get(i);
                chunk.setTargetEmbedding(embedding);
                if (embedding.length == 1024) {
                    chunk.setEmbedding(embedding);
                }
                chunk.setEmbeddingModel(embeddingModel);
                chunk.setMeta(doc.getMeta());
                chunkEntities.add(chunk);
            }

            // 5. 分块替换、文档激活和状态更新必须原子提交。
            boolean activated = commit(doc, chunkEntities, contentHash,
                profileCode == null || profileCode.isBlank() ? doc.getSpaceId() + ":" + embeddingModel : profileCode,
                space.getDatasetId());
            if (activated) {
                log.info("文档摄入完成: docId={}, chunks={}", docId, chunks.size());
                recordIngestMetrics(space.getSpaceCode(), "completed", pipelineStart);
            } else {
                log.info("文档已被更新版本取代，不再激活: docId={}", docId);
                recordIngestMetrics(space.getSpaceCode(), "superseded", pipelineStart);
            }
        } catch (Exception e) {
            log.error("文档摄入失败: docId={}", docId, e);
            String failureStatus = e instanceof OcrRequiredException
                ? IngestionStatus.NEEDS_OCR.getCode() : IngestionStatus.FAILED.getCode();
            updateStatus(docId, failureStatus, null, e.getMessage());
            if (space != null) {
                recordIngestMetrics(space.getSpaceCode(), "failed", pipelineStart);
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new KmaException("文档摄入失败: " + e.getMessage(), e);
        }
    }

    private boolean commit(KnowledgeDoc doc, List<KnowledgeChunk> chunks, String contentHash,
                           String profileCode, Long datasetId) {
        Boolean activated = knowledgeTransactionTemplate.execute(status -> {
            if (datasetId != null && datasetMapper != null) {
                String activeProfile = datasetMapper.selectEmbeddingProfileForUpdate(datasetId);
                if (activeProfile != null && !activeProfile.equals(profileCode)) {
                    throw new KmaException(409, "Embedding Profile 已在向量化期间切换，请按新 Profile 重试");
                }
            }
            boolean publicationManaged = Boolean.TRUE.equals(doc.getPublicationManaged());
            if (!publicationManaged && doc.getExternalRef() != null && !doc.getExternalRef().isBlank()) {
                Long latestVersion = docMapper.selectLatestSourceVersionForUpdate(
                    doc.getSpaceId(), doc.getExternalRef());
                long currentVersion = doc.getSourceVersion() == null ? 1L : doc.getSourceVersion();
                if (latestVersion != null && latestVersion > currentVersion) {
                    KnowledgeDoc superseded = new KnowledgeDoc();
                    superseded.setDocId(doc.getDocId());
                    superseded.setParseStatus(IngestionStatus.SUPERSEDED.getCode());
                    superseded.setIsActive(false);
                    superseded.setErrorMessage("已被更高来源版本取代: " + latestVersion);
                    superseded.setUpdateTime(java.time.LocalDateTime.now());
                    docMapper.updateById(superseded);
                    return false;
                }
            }

            chunkMapper.deleteByDocId(doc.getDocId());
            for (KnowledgeChunk chunk : chunks) {
                chunkMapper.insert(chunk);
                KnowledgeChunkEmbedding embedding = new KnowledgeChunkEmbedding();
                embedding.setChunkId(chunk.getChunkId());
                embedding.setSpaceId(chunk.getSpaceId());
                embedding.setProfileCode(profileCode);
                embedding.setModelName(chunk.getEmbeddingModel());
                embedding.setDimension(chunk.getTargetEmbedding().length);
                embedding.setEmbedding(chunk.getTargetEmbedding());
                embedding.setActive(true);
                embedding.setCreateTime(java.time.LocalDateTime.now());
                chunkEmbeddingMapper.insert(embedding);
            }

            if (!publicationManaged && doc.getExternalRef() != null && !doc.getExternalRef().isBlank()) {
                KnowledgeDoc deactivate = new KnowledgeDoc();
                deactivate.setIsActive(false);
                deactivate.setUpdateTime(java.time.LocalDateTime.now());
                docMapper.update(deactivate, new LambdaUpdateWrapper<KnowledgeDoc>()
                    .eq(KnowledgeDoc::getSpaceId, doc.getSpaceId())
                    .eq(KnowledgeDoc::getExternalRef, doc.getExternalRef())
                    .eq(KnowledgeDoc::getIsActive, true)
                    .ne(KnowledgeDoc::getDocId, doc.getDocId()));
            }

            KnowledgeDoc completed = new KnowledgeDoc();
            completed.setDocId(doc.getDocId());
            completed.setParseStatus(IngestionStatus.COMPLETED.getCode());
            completed.setChunkCount(chunks.size());
            completed.setContentHash(contentHash);
            completed.setErrorMessage("");
            completed.setIsActive(!publicationManaged);
            if (!publicationManaged) {
                completed.setActivatedAt(java.time.LocalDateTime.now());
            }
            completed.setUpdateTime(java.time.LocalDateTime.now());
            docMapper.updateById(completed);
            return true;
        });
        return Boolean.TRUE.equals(activated);
    }

    private void recordIngestMetrics(String spaceCode, String status, long start) {
        try {
            metricsRecorder.recordIngestLatency(spaceCode, System.currentTimeMillis() - start);
            metricsRecorder.recordIngestStatus(spaceCode, status);
        } catch (Exception ex) {
            log.warn("摄入指标埋点失败", ex);
        }
    }

    private String parseDocument(KnowledgeDoc doc) throws IOException {
        if (doc.getStoragePath() == null || doc.getStoragePath().isEmpty()) {
            throw new IOException("文档存储路径为空");
        }
        try (var fis = knowledgeStorage.open(doc.getStoragePath())) {
            String mimeType = doc.getMimeType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = FileUtil.getMimeType(doc.getStoragePath());
            }
            return extractorRegistry.extract(fis, mimeType);
        }
    }

    private List<String> splitText(String text, KnowledgeSpace space) {
        ChunkOptions options = new ChunkOptions();
        String type = "fixed_size";
        // 优先使用空间级分块策略
        if (space.getChunkStrategy() != null && !space.getChunkStrategy().isEmpty()) {
            // 简单解析 JSON：{ "type": "fixed_size", "chunkSize": 512, "overlap": 50 }
            type = parseChunkStrategy(space.getChunkStrategy(), options);
        }
        ChunkStrategy strategy = chunkStrategyFactory.get(type);
        return strategy.split(text, options);
    }

    private String parseChunkStrategy(String json, ChunkOptions options) {
        String type = "fixed_size";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
            if (node.has("type")) {
                type = node.get("type").asText("fixed_size");
            }
            if (node.has("chunkSize")) {
                options.setChunkSize(node.get("chunkSize").asInt(512));
            }
            if (node.has("overlap")) {
                options.setOverlap(node.get("overlap").asInt(50));
            }
        } catch (Exception e) {
            log.warn("解析分块策略失败，使用默认配置: {}", e.getMessage());
        }
        return type;
    }

    private List<float[]> embedInBatches(List<String> chunks, EmbeddingClient client, String model) throws Exception {
        int batchSize = properties.getEmbedding().getMaxBatchSize();
        List<float[]> allEmbeddings = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<String> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            long start = System.currentTimeMillis();
            List<float[]> embeddings = client.embed(batch);
            metricsRecorder.recordEmbeddingLatency(client.provider(), model, System.currentTimeMillis() - start);
            metricsRecorder.recordEmbeddingTexts(client.provider(), model, batch.size());
            allEmbeddings.addAll(embeddings);
        }
        return allEmbeddings;
    }

    private void validateEmbeddings(int expected, List<String> chunks, List<float[]> embeddings)
        throws com.kma.knowledge.rag.extract.NonRetryableIngestionException {
        if (expected != 768 && expected != 1024 && expected != 1536) {
            throw new com.kma.knowledge.rag.extract.NonRetryableIngestionException(
                "不支持的向量维度: " + expected);
        }
        if (embeddings.size() != chunks.size()) {
            throw new com.kma.knowledge.rag.extract.NonRetryableIngestionException(
                "Embedding 返回数量不匹配: expected=" + chunks.size() + ", actual=" + embeddings.size());
        }
        for (float[] embedding : embeddings) {
            if (embedding == null || embedding.length != expected) {
                throw new com.kma.knowledge.rag.extract.NonRetryableIngestionException(
                    "Embedding 维度不匹配: expected=" + expected + ", actual="
                        + (embedding == null ? 0 : embedding.length));
            }
        }
    }

    private void updateStatus(Long docId, String status, Integer chunkCount, String extra) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocId(docId);
        doc.setParseStatus(status);
        if (chunkCount != null) {
            doc.setChunkCount(chunkCount);
        }
        if (extra != null) {
            if (IngestionStatus.PARSING.getCode().equals(status) || IngestionStatus.CHUNKING.getCode().equals(status)) {
                doc.setContentHash(extra);
            } else {
                doc.setErrorMessage(extra);
            }
        }
        docMapper.updateById(doc);
    }

    private int estimateTokenCount(String text) {
        // 粗略估算：1 个 token 约 1.5 个汉字或 4 个英文字符，这里按字符数/2 兜底
        return text == null ? 0 : text.length() / 2;
    }
}



