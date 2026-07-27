package com.kma.knowledge.rag.pipeline;

import com.kma.knowledge.client.embedding.EmbeddingClient;
import com.kma.knowledge.client.embedding.EmbeddingClientFactory;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.entity.KnowledgeChunk;
import com.kma.knowledge.entity.KnowledgeChunkEmbedding;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.enums.IngestionStatus;
import com.kma.knowledge.mapper.KnowledgeChunkEmbeddingMapper;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import com.kma.knowledge.rag.chunk.ChunkStrategy;
import com.kma.knowledge.rag.chunk.ChunkStrategyFactory;
import com.kma.knowledge.rag.extract.DocumentExtractorRegistry;
import com.kma.knowledge.rag.retrieve.LexicalQueryAnalyzer;
import com.kma.knowledge.storage.KnowledgeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionPipelineTest {
    @Mock private KnowledgeDocMapper docMapper;
    @Mock private KnowledgeChunkMapper chunkMapper;
    @Mock private KnowledgeChunkEmbeddingMapper chunkEmbeddingMapper;
    @Mock private KnowledgeSpaceMapper spaceMapper;
    @Mock private DocumentExtractorRegistry extractorRegistry;
    @Mock private ChunkStrategyFactory chunkStrategyFactory;
    @Mock private EmbeddingClientFactory embeddingClientFactory;
    @Mock private RagMetricsRecorder metricsRecorder;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private LexicalQueryAnalyzer lexicalQueryAnalyzer;
    @Mock private KnowledgeStorage storage;
    @Mock private ChunkStrategy chunkStrategy;
    @Mock private EmbeddingClient embeddingClient;

    private KnowledgeProperties properties;
    private IngestionPipeline pipeline;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        properties.getEmbedding().setMaxBatchSize(16);
        pipeline = new IngestionPipeline(docMapper, chunkMapper, chunkEmbeddingMapper, spaceMapper,
            extractorRegistry, chunkStrategyFactory, embeddingClientFactory, properties, metricsRecorder,
            transactionTemplate, lexicalQueryAnalyzer, storage);
    }

    @Test
    void replacementFailureNeverDeactivatesThePreviousActiveDocument() throws Exception {
        KnowledgeDoc staging = new KnowledgeDoc();
        staging.setDocId(20L);
        staging.setSpaceId(2L);
        staging.setExternalRef("course:1");
        staging.setStoragePath("space/staging.txt");
        staging.setMimeType("text/plain");

        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(2L);
        space.setSpaceCode("space");
        space.setEmbeddingProvider("local");
        space.setEmbeddingModel("bge-m3");
        space.setEmbeddingDim(768);

        when(docMapper.selectById(20L)).thenReturn(staging);
        when(spaceMapper.selectById(2L)).thenReturn(space);
        when(storage.open(staging.getStoragePath())).thenReturn(
            new ByteArrayInputStream("new content".getBytes(StandardCharsets.UTF_8)));
        when(extractorRegistry.extract(any(), anyString())).thenReturn("new content");
        when(chunkStrategyFactory.get("fixed_size")).thenReturn(chunkStrategy);
        when(chunkStrategy.split(anyString(), any())).thenReturn(List.of("new content"));
        when(embeddingClientFactory.get("local")).thenReturn(embeddingClient);
        when(embeddingClient.embed(any())).thenReturn(List.of(new float[768]));
        when(embeddingClient.provider()).thenReturn("local");
        when(lexicalQueryAnalyzer.analyzeDocument("new content")).thenReturn("new content");
        doAnswer(invocation -> {
            KnowledgeChunk chunk = invocation.getArgument(0);
            chunk.setChunkId(100L);
            return 1;
        }).when(chunkMapper).insert(any(KnowledgeChunk.class));
        when(chunkEmbeddingMapper.insert(any(KnowledgeChunkEmbedding.class)))
            .thenThrow(new DataIntegrityViolationException("simulated write failure"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            org.springframework.transaction.support.TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());

        assertThatThrownBy(() -> pipeline.run(20L))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("simulated write failure");

        // 旧版本的停用位于同一提交事务的后半段；前序写入失败时绝不能执行停用。
        verify(docMapper, never()).update(any(KnowledgeDoc.class), any());
        ArgumentCaptor<KnowledgeDoc> statusUpdates = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(docMapper, org.mockito.Mockito.atLeastOnce()).updateById(statusUpdates.capture());
        assertThat(statusUpdates.getAllValues()).anyMatch(
            update -> IngestionStatus.FAILED.getCode().equals(update.getParseStatus()));
    }

    @Test
    void outOfOrderWorkerMarksOldVersionSupersededInsteadOfActivatingIt() throws Exception {
        KnowledgeDoc staging = new KnowledgeDoc();
        staging.setDocId(21L);
        staging.setSpaceId(2L);
        staging.setExternalRef("course:1");
        staging.setSourceVersion(2L);
        staging.setStoragePath("space/version-2.txt");
        staging.setMimeType("text/plain");
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(2L);
        space.setSpaceCode("space");
        space.setEmbeddingProvider("local");
        space.setEmbeddingModel("bge-m3");
        space.setEmbeddingDim(768);

        when(docMapper.selectById(21L)).thenReturn(staging);
        when(spaceMapper.selectById(2L)).thenReturn(space);
        when(storage.open(staging.getStoragePath())).thenReturn(
            new ByteArrayInputStream("old content".getBytes(StandardCharsets.UTF_8)));
        when(extractorRegistry.extract(any(), anyString())).thenReturn("old content");
        when(chunkStrategyFactory.get("fixed_size")).thenReturn(chunkStrategy);
        when(chunkStrategy.split(anyString(), any())).thenReturn(List.of("old content"));
        when(embeddingClientFactory.get("local")).thenReturn(embeddingClient);
        when(embeddingClient.embed(any())).thenReturn(List.of(new float[768]));
        when(embeddingClient.provider()).thenReturn("local");
        when(lexicalQueryAnalyzer.analyzeDocument("old content")).thenReturn("old content");
        when(docMapper.selectLatestSourceVersionForUpdate(2L, "course:1")).thenReturn(3L);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            org.springframework.transaction.support.TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());

        pipeline.run(21L);

        verify(chunkMapper, never()).deleteByDocId(any());
        verify(chunkMapper, never()).insert(any(KnowledgeChunk.class));
        verify(chunkEmbeddingMapper, never()).insert(any(KnowledgeChunkEmbedding.class));
        verify(docMapper, never()).update(any(KnowledgeDoc.class), any());
        ArgumentCaptor<KnowledgeDoc> updates = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(docMapper, atLeastOnce()).updateById(updates.capture());
        assertThat(updates.getAllValues()).anyMatch(update ->
            IngestionStatus.SUPERSEDED.getCode().equals(update.getParseStatus())
                && Boolean.FALSE.equals(update.getIsActive()));
    }

    @Test
    void publicationManagedDocumentStaysInactiveUntilReviewPublishesIt() throws Exception {
        KnowledgeDoc governed = new KnowledgeDoc();
        governed.setDocId(22L);
        governed.setSpaceId(2L);
        governed.setExternalRef("party-policy:22");
        governed.setSourceVersion(1L);
        governed.setStoragePath("space/policy-22.txt");
        governed.setMimeType("text/plain");
        governed.setPublicationManaged(true);

        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(2L);
        space.setSpaceCode("party-policy");
        space.setEmbeddingProvider("local");
        space.setEmbeddingModel("bge-m3");
        space.setEmbeddingDim(768);

        when(docMapper.selectById(22L)).thenReturn(governed);
        when(spaceMapper.selectById(2L)).thenReturn(space);
        when(storage.open(governed.getStoragePath())).thenReturn(
            new ByteArrayInputStream("党建政策正文".getBytes(StandardCharsets.UTF_8)));
        when(extractorRegistry.extract(any(), anyString())).thenReturn("党建政策正文");
        when(chunkStrategyFactory.get("fixed_size")).thenReturn(chunkStrategy);
        when(chunkStrategy.split(anyString(), any())).thenReturn(List.of("党建政策正文"));
        when(embeddingClientFactory.get("local")).thenReturn(embeddingClient);
        when(embeddingClient.embed(any())).thenReturn(List.of(new float[768]));
        when(embeddingClient.provider()).thenReturn("local");
        when(lexicalQueryAnalyzer.analyzeDocument("党建政策正文")).thenReturn("党建政策正文");
        doAnswer(invocation -> {
            KnowledgeChunk chunk = invocation.getArgument(0);
            chunk.setChunkId(102L);
            return 1;
        }).when(chunkMapper).insert(any(KnowledgeChunk.class));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            org.springframework.transaction.support.TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());

        pipeline.run(22L);

        verify(docMapper, never()).selectLatestSourceVersionForUpdate(any(), anyString());
        verify(docMapper, never()).update(any(KnowledgeDoc.class), any());
        ArgumentCaptor<KnowledgeDoc> updates = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(docMapper, atLeastOnce()).updateById(updates.capture());
        assertThat(updates.getAllValues()).anyMatch(update ->
            IngestionStatus.COMPLETED.getCode().equals(update.getParseStatus())
                && Boolean.FALSE.equals(update.getIsActive())
                && update.getActivatedAt() == null);
    }
}
