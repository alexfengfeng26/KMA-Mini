package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.client.embedding.EmbeddingClient;
import com.kma.knowledge.client.embedding.EmbeddingClientFactory;
import com.kma.knowledge.model.ResolvedModelProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable staged vector rebuild with profile isolation and an atomic activation switch. */
@Slf4j
@Service
public class EmbeddingRebuildService {
    private static final int BATCH_SIZE = 32;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final EmbeddingClientFactory clientFactory;
    private final ModelProfileResolver profileResolver;
    private final ObjectMapper objectMapper;
    private final boolean rebuildWorkerEnabled;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();

    public EmbeddingRebuildService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                                   @Qualifier("knowledgeTransactionTemplate") TransactionTemplate transactionTemplate,
                                   EmbeddingClientFactory clientFactory, ModelProfileResolver profileResolver,
                                   ObjectMapper objectMapper,
                                   @Value("${knowledge.embedding.rebuild-worker-enabled:true}") boolean rebuildWorkerEnabled) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.clientFactory = clientFactory;
        this.profileResolver = profileResolver;
        this.objectMapper = objectMapper;
        this.rebuildWorkerEnabled = rebuildWorkerEnabled;
    }

    public Long start(Long datasetId, String targetProfileCode) {
        ResolvedModelProfile target = profileResolver.resolve(targetProfileCode, "embedding");
        Map<String, Object> dataset = dataset(datasetId);
        String source = (String) dataset.get("embedding_profile_code");
        if (targetProfileCode.equals(source)) throw new KmaException(409, "目标 Profile 已是当前激活版本");
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM knowledge_chunk c JOIN knowledge_space s ON s.space_id=c.space_id
            JOIN knowledge_doc d ON d.doc_id=c.doc_id
            WHERE s.dataset_id=? AND d.is_active=TRUE
            """, Long.class, datasetId);
        try {
            return jdbc.queryForObject("""
                INSERT INTO kma_embedding_rebuild_job
                    (dataset_id,source_profile_code,target_profile_code,total_chunks,status)
                VALUES (?,?,?,?,'pending') RETURNING job_id
                """, Long.class, datasetId, source, target.getProfileCode(), total);
        } catch (Exception ex) {
            throw new KmaException(409, "数据集已有进行中的向量重建任务");
        }
    }

    public List<Map<String, Object>> list(Long datasetId) {
        dataset(datasetId);
        return jdbc.queryForList("""
            SELECT job_id,dataset_id,source_profile_code,target_profile_code,status,total_chunks,
                   processed_chunks,cursor_chunk_id,metrics,error_message,create_time,update_time,activated_at
            FROM kma_embedding_rebuild_job WHERE dataset_id=? ORDER BY create_time DESC LIMIT 100
            """, datasetId);
    }

    public Map<String, Object> activate(Long jobId) {
        Map<String, Object> job = job(jobId);
        if (!"ready".equals(job.get("status"))) throw new KmaException(409, "向量重建尚未通过双读校验");
        String targetCode = (String) job.get("target_profile_code");
        ResolvedModelProfile target = profileResolver.resolve(targetCode, "embedding");
        Long datasetId = ((Number) job.get("dataset_id")).longValue();
        transactionTemplate.executeWithoutResult(status -> {
            Map<String, Object> locked = jdbc.queryForMap(
                "SELECT dataset_id,embedding_profile_code FROM knowledge_dataset WHERE dataset_id=? FOR UPDATE",
                datasetId);
            if (!java.util.Objects.equals(locked.get("embedding_profile_code"), job.get("source_profile_code"))) {
                throw new KmaException(409, "数据集 Profile 已变化，拒绝激活过期重建任务");
            }
            int changed = jdbc.update("""
                UPDATE kma_embedding_rebuild_job SET status='activated',activated_at=now(),update_time=now(),
                    lease_owner=NULL,lease_until=NULL
                WHERE job_id=? AND status='ready'
                """, jobId);
            if (changed != 1) throw new KmaException(409, "向量重建状态已变化，请刷新后重试");
            jdbc.update("""
                UPDATE knowledge_chunk_embedding e SET active=(e.profile_code=?)
                FROM knowledge_chunk c JOIN knowledge_space s ON s.space_id=c.space_id
                WHERE e.chunk_id=c.chunk_id AND s.dataset_id=?
                """, targetCode, datasetId);
            jdbc.update("UPDATE knowledge_dataset SET embedding_profile_code=?,update_time=now() WHERE dataset_id=?",
                targetCode, datasetId);
            jdbc.update("""
                UPDATE knowledge_space SET embedding_provider=?,embedding_model=?,embedding_dim=?,update_time=now()
                WHERE dataset_id=?
                """, target.getProvider(), target.getModelName(), target.getDimension(), datasetId);
        });
        return job(jobId);
    }

    @Scheduled(fixedDelayString = "${knowledge.embedding.rebuild-fixed-delay:2000}")
    public void work() {
        if (!rebuildWorkerEnabled) return;
        Map<String, Object> claimed = claim();
        if (claimed == null) return;
        process(claimed);
    }

    void process(Map<String, Object> job) {
        Long jobId = ((Number) job.get("job_id")).longValue();
        try {
            long cursor = job.get("cursor_chunk_id") == null ? 0L : ((Number) job.get("cursor_chunk_id")).longValue();
            Long datasetId = ((Number) job.get("dataset_id")).longValue();
            String targetCode = (String) job.get("target_profile_code");
            ResolvedModelProfile target = profileResolver.resolve(targetCode, "embedding");
            List<ChunkRow> rows = jdbc.query("""
                SELECT c.chunk_id,c.space_id,c.content FROM knowledge_chunk c
                JOIN knowledge_space s ON s.space_id=c.space_id JOIN knowledge_doc d ON d.doc_id=c.doc_id
                WHERE s.dataset_id=? AND d.is_active=TRUE AND c.chunk_id>?
                ORDER BY c.chunk_id LIMIT ?
                """, (rs, n) -> new ChunkRow(rs.getLong(1), rs.getLong(2), rs.getString(3)),
                datasetId, cursor, BATCH_SIZE);
            if (rows.isEmpty()) {
                validate(jobId, datasetId, (String) job.get("source_profile_code"),
                    targetCode, target.getDimension());
                return;
            }
            EmbeddingClient client = clientFactory.getByProfile(targetCode);
            List<float[]> vectors = client.embed(rows.stream().map(ChunkRow::content).toList());
            if (vectors.size() != rows.size()) throw new KmaException(502, "Embedding 返回数量不匹配");
            transactionTemplate.executeWithoutResult(status -> {
                for (int i = 0; i < rows.size(); i++) {
                    float[] vector = vectors.get(i);
                    if (vector == null || vector.length != target.getDimension()) {
                        throw new KmaException(502, "Embedding 返回维度与目标 Profile 不一致");
                    }
                    ChunkRow row = rows.get(i);
                    jdbc.update("""
                        INSERT INTO knowledge_chunk_embedding
                            (chunk_id,space_id,profile_code,model_name,dimension,embedding,active)
                        VALUES (?,?,?,?,?,?::vector,FALSE)
                        ON CONFLICT (chunk_id,profile_code) DO UPDATE SET
                            model_name=EXCLUDED.model_name,dimension=EXCLUDED.dimension,
                            embedding=EXCLUDED.embedding,active=FALSE,create_time=now()
                        """, row.chunkId(), row.spaceId(), targetCode, target.getModelName(),
                        target.getDimension(), vectorLiteral(vector));
                }
                jdbc.update("""
                    UPDATE kma_embedding_rebuild_job SET processed_chunks=processed_chunks+?,cursor_chunk_id=?,
                        update_time=now(),lease_owner=NULL,lease_until=NULL WHERE job_id=?
                    """, rows.size(), rows.get(rows.size() - 1).chunkId(), jobId);
            });
        } catch (Exception ex) {
            log.error("向量重建任务失败: jobId={}", jobId, ex);
            jdbc.update("""
                UPDATE kma_embedding_rebuild_job SET status='failed',error_message=?,update_time=now(),
                    lease_owner=NULL,lease_until=NULL WHERE job_id=?
                """, truncate(ex.getMessage()), jobId);
        }
    }

    private void validate(Long jobId, Long datasetId, String sourceCode,
                          String targetCode, int dimension) throws Exception {
        long targetCount = jdbc.queryForObject("""
            SELECT count(*) FROM knowledge_chunk_embedding e JOIN knowledge_chunk c ON c.chunk_id=e.chunk_id
            JOIN knowledge_space s ON s.space_id=c.space_id JOIN knowledge_doc d ON d.doc_id=c.doc_id
            WHERE s.dataset_id=? AND d.is_active=TRUE AND e.profile_code=? AND e.dimension=?
            """, Long.class, datasetId, targetCode, dimension);
        long total = jdbc.queryForObject("SELECT total_chunks FROM kma_embedding_rebuild_job WHERE job_id=?",
            Long.class, jobId);
        double dualReadCoverage = total == 0 ? 1.0 : (double) targetCount / total;
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("targetCount", targetCount);
        metrics.put("expectedCount", total);
        metrics.put("dualReadCoverage", dualReadCoverage);
        if (sourceCode != null && !sourceCode.isBlank()) {
            ResolvedModelProfile source = profileResolver.resolve(sourceCode, "embedding");
            RankingComparison comparison = compareRankings(
                datasetId, sourceCode, source.getDimension(), targetCode, dimension);
            metrics.put("rankingOverlapAt5", comparison.averageOverlap());
            metrics.put("comparisonQueries", comparison.queries());
        }
        String status = targetCount == total ? "ready" : "failed";
        jdbc.update("""
            UPDATE kma_embedding_rebuild_job SET status=?,metrics=?::jsonb,error_message=?,update_time=now(),
                lease_owner=NULL,lease_until=NULL WHERE job_id=?
            """, status, objectMapper.writeValueAsString(metrics),
            targetCount == total ? null : "目标向量数量未通过双读覆盖校验", jobId);
    }

    private Map<String, Object> claim() {
        List<Map<String, Object>> values = jdbc.queryForList("""
            UPDATE kma_embedding_rebuild_job SET status='running',lease_owner=?,lease_until=now()+interval '60 seconds',update_time=now()
            WHERE job_id=(SELECT job_id FROM kma_embedding_rebuild_job
                WHERE status IN ('pending','running') AND (lease_until IS NULL OR lease_until<now())
                ORDER BY create_time FOR UPDATE SKIP LOCKED LIMIT 1)
            RETURNING job_id,dataset_id,source_profile_code,target_profile_code,cursor_chunk_id
            """, workerId);
        return values.isEmpty() ? null : values.get(0);
    }

    private Map<String, Object> dataset(Long id) {
        List<Map<String, Object>> values = jdbc.queryForList(
            "SELECT dataset_id,embedding_profile_code FROM knowledge_dataset WHERE dataset_id=?", id);
        if (values.isEmpty()) throw new KmaException(404, "数据集不存在");
        return values.get(0);
    }

    private RankingComparison compareRankings(Long datasetId, String sourceCode, int sourceDimension,
                                                 String targetCode, int targetDimension) {
        List<SampleVector> samples = jdbc.query("""
            SELECT source.chunk_id,source.embedding::text,target.embedding::text
            FROM knowledge_chunk_embedding source
            JOIN knowledge_chunk_embedding target ON target.chunk_id=source.chunk_id
            JOIN knowledge_chunk c ON c.chunk_id=source.chunk_id
            JOIN knowledge_space s ON s.space_id=c.space_id
            JOIN knowledge_doc d ON d.doc_id=c.doc_id
            WHERE s.dataset_id=? AND d.is_active=TRUE
              AND source.profile_code=? AND target.profile_code=?
            ORDER BY source.chunk_id LIMIT 10
            """, (rs, row) -> new SampleVector(rs.getLong(1), rs.getString(2), rs.getString(3)),
            datasetId, sourceCode, targetCode);
        double sum = 0.0;
        for (SampleVector sample : samples) {
            List<Long> sourceNeighbors = nearest(datasetId, sourceCode, sourceDimension,
                sample.chunkId(), sample.sourceVector());
            List<Long> targetNeighbors = nearest(datasetId, targetCode, targetDimension,
                sample.chunkId(), sample.targetVector());
            java.util.Set<Long> union = new java.util.LinkedHashSet<>(sourceNeighbors);
            union.addAll(targetNeighbors);
            java.util.Set<Long> intersection = new java.util.LinkedHashSet<>(sourceNeighbors);
            intersection.retainAll(targetNeighbors);
            sum += union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
        }
        return new RankingComparison(samples.isEmpty() ? 1.0 : sum / samples.size(), samples.size());
    }

    private List<Long> nearest(Long datasetId, String profileCode, int dimension,
                               Long excludedChunkId, String queryVector) {
        if (dimension != 768 && dimension != 1024 && dimension != 1536) {
            throw new KmaException(400, "不支持的向量维度: " + dimension);
        }
        String sql = """
            SELECT e.chunk_id FROM knowledge_chunk_embedding e
            JOIN knowledge_chunk c ON c.chunk_id=e.chunk_id
            JOIN knowledge_space s ON s.space_id=c.space_id
            JOIN knowledge_doc d ON d.doc_id=c.doc_id
            WHERE s.dataset_id=? AND d.is_active=TRUE
              AND e.profile_code=? AND e.dimension=? AND e.chunk_id<>?
            ORDER BY e.embedding::vector(%d) <=> ?::vector(%d) LIMIT 5
            """.formatted(dimension, dimension);
        return jdbc.query(sql, (rs, row) -> rs.getLong(1), datasetId, profileCode, dimension,
            excludedChunkId, queryVector);
    }

    private Map<String, Object> job(Long id) {
        List<Map<String, Object>> values = jdbc.queryForList(
            "SELECT * FROM kma_embedding_rebuild_job WHERE job_id=?", id);
        if (values.isEmpty()) throw new KmaException(404, "向量重建任务不存在");
        return values.get(0);
    }

    private String vectorLiteral(float[] values) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) value.append(',');
            value.append(Float.isFinite(values[i]) ? values[i] : 0F);
        }
        return value.append(']').toString();
    }

    private String truncate(String value) { return value == null ? "未知错误" : value.substring(0, Math.min(1000, value.length())); }
    private record ChunkRow(long chunkId, long spaceId, String content) {}
    private record SampleVector(long chunkId, String sourceVector, String targetVector) {}
    private record RankingComparison(double averageOverlap, int queries) {}
}
