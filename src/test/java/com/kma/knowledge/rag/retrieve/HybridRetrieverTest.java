package com.kma.knowledge.rag.retrieve;

import com.kma.knowledge.client.rerank.RerankClient;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 混合检索器单元测试
 *
 * @author party
 * @date 2026/07/02
 */
@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {

    @Mock
    private VectorRetriever vectorRetriever;
    @Mock
    private FullTextRetriever fullTextRetriever;
    @Mock
    private RerankClient rerankClient;
    @Mock
    private KnowledgeSpaceMapper spaceMapper;
    @Mock
    private RagMetricsRecorder metricsRecorder;

    private HybridRetriever hybridRetriever;

    @BeforeEach
    void setUp() {
        hybridRetriever = new HybridRetriever(vectorRetriever, fullTextRetriever, rerankClient, spaceMapper, metricsRecorder);
    }

    @Test
    void shouldMergeAndRerankHits() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace());

        ChunkHitVO vectorHit = buildHit(1L, "vector content");
        ChunkHitVO fullTextHit = buildHit(2L, "fulltext content");
        ChunkHitVO duplicateHit = buildHit(1L, "vector content");

        when(vectorRetriever.retrieve(any())).thenReturn(List.of(vectorHit, duplicateHit));
        when(fullTextRetriever.retrieve(any())).thenReturn(List.of(fullTextHit));
        when(rerankClient.score(any(), any())).thenReturn(List.of(0.9, 0.8));

        RetrieveRequest request = new RetrieveRequest();
        request.setSpaceCode("space");
        request.setQuery("党建");
        request.setTopK(2);

        List<ChunkHitVO> hits = hybridRetriever.retrieve(request);

        assertEquals(2, hits.size());
        assertEquals(0.9, hits.get(0).getScore(), 0.001);
        assertEquals(0.8, hits.get(1).getScore(), 0.001);
        assertTrue(hits.stream().anyMatch(h -> "rerank".equals(h.getSourceStage())));
    }

    @Test
    void shouldFilterByScoreThreshold() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace());

        ChunkHitVO hit1 = buildHit(1L, "high");
        ChunkHitVO hit2 = buildHit(2L, "low");

        when(vectorRetriever.retrieve(any())).thenReturn(List.of(hit1, hit2));
        when(fullTextRetriever.retrieve(any())).thenReturn(List.of());
        when(rerankClient.score(any(), any())).thenReturn(List.of(0.9, 0.2));

        RetrieveRequest request = new RetrieveRequest();
        request.setSpaceCode("space");
        request.setQuery("党建");
        request.setTopK(5);
        request.setScoreThreshold(new BigDecimal("0.35"));

        List<ChunkHitVO> hits = hybridRetriever.retrieve(request);

        assertEquals(1, hits.size());
        assertEquals(0.9, hits.get(0).getScore(), 0.001);
    }

    @Test
    void shouldReturnEmptyWhenNoCandidates() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace());
        when(vectorRetriever.retrieve(any())).thenReturn(List.of());
        when(fullTextRetriever.retrieve(any())).thenReturn(List.of());

        RetrieveRequest request = new RetrieveRequest();
        request.setSpaceCode("space");
        request.setQuery("党建");

        List<ChunkHitVO> hits = hybridRetriever.retrieve(request);

        assertTrue(hits.isEmpty());
    }

    @Test
    void shouldFallBackToFullTextWhenVectorServiceIsUnavailable() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace());
        when(vectorRetriever.retrieve(any())).thenThrow(new IllegalStateException("embedding unavailable"));
        when(fullTextRetriever.retrieve(any())).thenReturn(List.of(buildHit(2L, "三会一课")));
        when(rerankClient.score(any(), any())).thenReturn(List.of(0.88));

        List<ChunkHitVO> hits = hybridRetriever.retrieve(request());

        assertEquals(1, hits.size());
        assertEquals(0.88, hits.get(0).getScore(), 0.001);
        assertEquals("rerank", hits.get(0).getSourceStage());
    }

    @Test
    void shouldKeepRrfOrderWhenRerankServiceIsUnavailable() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace());
        when(vectorRetriever.retrieve(any())).thenReturn(List.of());
        when(fullTextRetriever.retrieve(any())).thenReturn(List.of(
            buildHit(1L, "党员大会"), buildHit(2L, "党小组会")));
        when(rerankClient.score(any(), any())).thenThrow(new IllegalStateException("rerank unavailable"));

        List<ChunkHitVO> hits = hybridRetriever.retrieve(request());

        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).getChunkId());
        assertEquals("hybrid", hits.get(0).getSourceStage());
        assertTrue(hits.get(0).getScore() > hits.get(1).getScore());
    }

    private RetrieveRequest request() {
        RetrieveRequest request = new RetrieveRequest();
        request.setSpaceCode("space");
        request.setQuery("党建");
        request.setTopK(5);
        return request;
    }

    private KnowledgeSpace buildSpace() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(1L);
        space.setSpaceCode("space");
        space.setDefaultTopK(5);
        space.setScoreThreshold(new BigDecimal("0.35"));
        return space;
    }

    private ChunkHitVO buildHit(Long chunkId, String content) {
        ChunkHitVO hit = new ChunkHitVO();
        hit.setChunkId(chunkId);
        hit.setDocId(chunkId);
        hit.setContent(content);
        hit.setSourceStage("vector");
        return hit;
    }
}



