package com.kma.knowledge.rag.retrieve;

import com.kma.knowledge.client.rerank.RerankClient;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合检索器（默认）
 * <p>
 * 向量召回 + 全文召回 → 去重合并 → Rerank 重排序 → TopK
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
@Slf4j
@RequiredArgsConstructor
public class HybridRetriever implements Retriever {

    private static final int RRF_K = 60;

    private final VectorRetriever vectorRetriever;
    private final FullTextRetriever fullTextRetriever;
    private final RerankClient rerankClient;
    private final KnowledgeSpaceMapper spaceMapper;
    private final RagMetricsRecorder metricsRecorder;

    @Override
    public List<ChunkHitVO> retrieve(RetrieveRequest request) {
        return retrieveInternal(request, false);
    }

    /**
     * 检索并返回调试信息
     */
    public RetrieveDebugContext retrieveDebug(RetrieveRequest request) {
        return retrieveInternalDebug(request);
    }

    private List<ChunkHitVO> retrieveInternal(RetrieveRequest request, boolean debug) {
        RetrieveDebugContext ctx = retrieveInternalDebug(request);
        return ctx.getFinalHits();
    }

    private RetrieveDebugContext retrieveInternalDebug(RetrieveRequest request) {
        long start = System.currentTimeMillis();
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(request.getSpaceCode());
        String spaceCode = request.getSpaceCode();
        int topK = request.getTopK() != null ? request.getTopK() : (space != null ? space.getDefaultTopK() : 5);
        BigDecimal threshold = request.getScoreThreshold() != null
            ? request.getScoreThreshold()
            : (space != null ? space.getScoreThreshold() : new BigDecimal("0.35"));

        long t1 = System.currentTimeMillis();
        List<ChunkHitVO> vectorHits;
        try {
            vectorHits = vectorRetriever.retrieve(request);
        } catch (RuntimeException ex) {
            log.warn("向量召回不可用，降级为全文召回: spaceCode={}, error={}", spaceCode, ex.getMessage());
            vectorHits = List.of();
        }
        long t2 = System.currentTimeMillis();
        List<ChunkHitVO> fullTextHits;
        try {
            fullTextHits = fullTextRetriever.retrieve(request);
        } catch (RuntimeException ex) {
            log.warn("全文召回不可用，继续使用向量候选: spaceCode={}, error={}", spaceCode, ex.getMessage());
            fullTextHits = List.of();
        }
        long t3 = System.currentTimeMillis();

        // Reciprocal Rank Fusion：使用排名而不是混用不可比较的向量/全文原始分数。
        Map<Long, ChunkHitVO> candidateMap = new LinkedHashMap<>();
        addRankedHits(candidateMap, vectorHits, true);
        addRankedHits(candidateMap, fullTextHits, false);
        double maxRrf = 2.0 / (RRF_K + 1.0);
        List<ChunkHitVO> candidates = new ArrayList<>(candidateMap.values());
        candidates.forEach(hit -> {
            double normalized = Math.min(1.0, hit.getRrfScore() / maxRrf);
            hit.setRrfScore(normalized);
            hit.setScore(normalized);
            hit.setSourceStage("hybrid");
        });
        candidates.sort(Comparator.comparing(ChunkHitVO::getScore).reversed());

        long t4 = System.currentTimeMillis();
        List<ChunkHitVO> reranked = rerank(candidates, request.getQuery());
        long t5 = System.currentTimeMillis();

        // 阈值过滤 + TopK
        double minScore = threshold.doubleValue();
        List<ChunkHitVO> finalHits = reranked.stream()
            .filter(h -> h.getScore() != null && h.getScore() >= minScore)
            .limit(topK)
            .collect(Collectors.toList());

        long total = System.currentTimeMillis() - start;

        // 指标埋点
        try {
            metricsRecorder.recordRetrieveLatency(spaceCode, total);
            metricsRecorder.recordRetrieveHitCount(spaceCode, finalHits.size());
            metricsRecorder.recordRetrieveStageLatency(spaceCode, "vector", t2 - t1);
            metricsRecorder.recordRetrieveStageLatency(spaceCode, "fulltext", t3 - t2);
            metricsRecorder.recordRetrieveStageLatency(spaceCode, "merge", t4 - t3);
            metricsRecorder.recordRetrieveStageLatency(spaceCode, "rerank", t5 - t4);
        } catch (Exception e) {
            log.warn("检索指标埋点失败", e);
        }

        RetrieveDebugContext ctx = new RetrieveDebugContext();
        ctx.setVectorHits(copyHits(vectorHits));
        ctx.setFullTextHits(copyHits(fullTextHits));
        ctx.setRerankedHits(copyHits(reranked));
        ctx.setFinalHits(finalHits);
        ctx.setLatency(Map.of(
            "embed", t1 - start,
            "vectorSearch", t2 - t1,
            "fullTextSearch", t3 - t2,
            "merge", t4 - t3,
            "rerank", t5 - t4,
            "total", total
        ));
        return ctx;
    }

    private void addRankedHits(Map<Long, ChunkHitVO> candidates, List<ChunkHitVO> hits, boolean vector) {
        Set<Long> seen = new HashSet<>();
        for (int index = 0; index < hits.size(); index++) {
            ChunkHitVO source = hits.get(index);
            if (source.getChunkId() == null || !seen.add(source.getChunkId())) {
                continue;
            }
            ChunkHitVO target = candidates.computeIfAbsent(source.getChunkId(), ignored -> copyHit(source));
            double rawRrf = target.getRrfScore() == null ? 0.0 : target.getRrfScore();
            target.setRrfScore(rawRrf + 1.0 / (RRF_K + index + 1.0));
            if (vector) {
                target.setVectorScore(source.getVectorScore() != null ? source.getVectorScore() : source.getScore());
            } else {
                target.setFullTextScore(source.getFullTextScore() != null ? source.getFullTextScore() : source.getScore());
            }
        }
    }

    private List<ChunkHitVO> rerank(List<ChunkHitVO> candidates, String query) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<String> texts = candidates.stream()
            .map(ChunkHitVO::getContent)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        List<Double> scores;
        try {
            scores = rerankClient.score(query, texts);
        } catch (RuntimeException ex) {
            log.warn("重排服务不可用，保留 RRF 排序: error={}", ex.getMessage());
            return candidates;
        }
        for (int i = 0; i < candidates.size() && i < scores.size(); i++) {
            candidates.get(i).setScore(scores.get(i));
            candidates.get(i).setRerankScore(scores.get(i));
            candidates.get(i).setSourceStage("rerank");
        }
        candidates.sort(Comparator.comparing(ChunkHitVO::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        return candidates;
    }

    private List<ChunkHitVO> copyHits(List<ChunkHitVO> hits) {
        if (hits == null) {
            return new ArrayList<>();
        }
        return hits.stream().map(this::copyHit).collect(Collectors.toList());
    }

    private ChunkHitVO copyHit(ChunkHitVO src) {
        ChunkHitVO dest = new ChunkHitVO();
        dest.setChunkId(src.getChunkId());
        dest.setDocId(src.getDocId());
        dest.setSpaceId(src.getSpaceId());
        dest.setSpaceCode(src.getSpaceCode());
        dest.setDocTitle(src.getDocTitle());
        dest.setSourceTag(src.getSourceTag());
        dest.setExternalRef(src.getExternalRef());
        dest.setContent(src.getContent());
        dest.setScore(src.getScore());
        dest.setVectorScore(src.getVectorScore());
        dest.setFullTextScore(src.getFullTextScore());
        dest.setRrfScore(src.getRrfScore());
        dest.setRerankScore(src.getRerankScore());
        dest.setSourceStage(src.getSourceStage());
        dest.setChunkIndex(src.getChunkIndex());
        dest.setMeta(src.getMeta());
        dest.setDocumentNumber(src.getDocumentNumber());
        dest.setIssuingAuthority(src.getIssuingAuthority());
        dest.setPublishDate(src.getPublishDate());
        dest.setValidityStatus(src.getValidityStatus());
        dest.setPageNumber(src.getPageNumber());
        dest.setSection(src.getSection());
        return dest;
    }

    /**
     * 检索调试上下文
     */
    public static class RetrieveDebugContext {
        private List<ChunkHitVO> vectorHits;
        private List<ChunkHitVO> fullTextHits;
        private List<ChunkHitVO> rerankedHits;
        private List<ChunkHitVO> finalHits;
        private Map<String, Long> latency;

        // getters / setters
        public List<ChunkHitVO> getVectorHits() { return vectorHits; }
        public void setVectorHits(List<ChunkHitVO> vectorHits) { this.vectorHits = vectorHits; }
        public List<ChunkHitVO> getFullTextHits() { return fullTextHits; }
        public void setFullTextHits(List<ChunkHitVO> fullTextHits) { this.fullTextHits = fullTextHits; }
        public List<ChunkHitVO> getRerankedHits() { return rerankedHits; }
        public void setRerankedHits(List<ChunkHitVO> rerankedHits) { this.rerankedHits = rerankedHits; }
        public List<ChunkHitVO> getFinalHits() { return finalHits; }
        public void setFinalHits(List<ChunkHitVO> finalHits) { this.finalHits = finalHits; }
        public Map<String, Long> getLatency() { return latency; }
        public void setLatency(Map<String, Long> latency) { this.latency = latency; }
    }
}



