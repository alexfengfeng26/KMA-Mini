package com.kma.knowledge.service.impl;

import com.kma.knowledge.service.KnowledgeMetricsService;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 知识库指标服务实现
 *
 * @author party
 * @date 2026/07/02
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeMetricsServiceImpl implements KnowledgeMetricsService {

    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getDashboardMetrics(String spaceCode) {
        Map<String, Object> ApiResult = new LinkedHashMap<>();

        ApiResult.put("qaTotal", count("knowledge.qa.status", "success", spaceCode) + count("knowledge.qa.status", "failed", spaceCode));
        ApiResult.put("qaSuccess", count("knowledge.qa.status", "success", spaceCode));
        ApiResult.put("qaFailed", count("knowledge.qa.status", "failed", spaceCode));
        ApiResult.put("qaAvgLatency", mean("knowledge.qa.latency", spaceCode));
        ApiResult.put("retrieveAvgLatency", mean("knowledge.retrieve.latency", spaceCode));
        ApiResult.put("llmAvgLatency", mean("knowledge.llm.latency", null));
        ApiResult.put("embeddingAvgLatency", mean("knowledge.embedding.latency", null));

        long promptTokens = total("knowledge.qa.tokens", "prompt", spaceCode);
        long completionTokens = total("knowledge.qa.tokens", "completion", spaceCode);
        ApiResult.put("promptTokens", promptTokens);
        ApiResult.put("completionTokens", completionTokens);
        ApiResult.put("totalTokens", promptTokens + completionTokens);

        ApiResult.put("ingestCompleted", count("knowledge.ingest.status", "completed", spaceCode));
        ApiResult.put("ingestFailed", count("knowledge.ingest.status", "failed", spaceCode));

        addCatalogMetrics(ApiResult, spaceCode);

        return ApiResult;
    }

    private void addCatalogMetrics(Map<String, Object> result, String spaceCode) {
        if (spaceCode == null || spaceCode.isBlank()) {
            result.put("docCount", queryCount("SELECT count(*) FROM knowledge_doc"));
            result.put("chunkCount", queryCount("SELECT count(*) FROM knowledge_chunk"));
            result.put("pendingTaskCount", queryCount("""
                SELECT count(*) FROM knowledge_feed_task
                WHERE status IN ('pending', 'processing')
                """));
            return;
        }

        result.put("docCount", queryCount("""
            SELECT count(*) FROM knowledge_doc d
            JOIN knowledge_space s ON s.space_id = d.space_id
            WHERE s.space_code = ?
            """, spaceCode));
        result.put("chunkCount", queryCount("""
            SELECT count(*) FROM knowledge_chunk c
            JOIN knowledge_space s ON s.space_id = c.space_id
            WHERE s.space_code = ?
            """, spaceCode));
        result.put("pendingTaskCount", queryCount("""
            SELECT count(*) FROM knowledge_feed_task
            WHERE space_code = ? AND status IN ('pending', 'processing')
            """, spaceCode));
    }

    private long queryCount(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private double mean(String name, String spaceCode) {
        double sum = 0;
        double count = 0;
        for (Meter meter : meterRegistry.getMeters()) {
            if (!name.equals(meter.getId().getName())) {
                continue;
            }
            if (spaceCode != null && !spaceCode.equals(meter.getId().getTag("space_code"))) {
                continue;
            }
            for (io.micrometer.core.instrument.Measurement m : meter.measure()) {
                if (m.getStatistic() == Statistic.TOTAL_TIME) {
                    sum += m.getValue();
                }
                if (m.getStatistic() == Statistic.COUNT) {
                    count += m.getValue();
                }
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private long count(String name, String status, String spaceCode) {
        long total = 0;
        for (Meter meter : meterRegistry.getMeters()) {
            if (!name.equals(meter.getId().getName())) {
                continue;
            }
            if (status != null && !status.equals(meter.getId().getTag("status"))) {
                continue;
            }
            if (spaceCode != null && !spaceCode.equals(meter.getId().getTag("space_code"))) {
                continue;
            }
            for (io.micrometer.core.instrument.Measurement m : meter.measure()) {
                if (m.getStatistic() == Statistic.COUNT) {
                    total += (long) m.getValue();
                }
            }
        }
        return total;
    }

    private long total(String name, String type, String spaceCode) {
        long total = 0;
        for (Meter meter : meterRegistry.getMeters()) {
            if (!name.equals(meter.getId().getName())) {
                continue;
            }
            if (type != null && !type.equals(meter.getId().getTag("type"))) {
                continue;
            }
            if (spaceCode != null && !spaceCode.equals(meter.getId().getTag("space_code"))) {
                continue;
            }
            for (io.micrometer.core.instrument.Measurement m : meter.measure()) {
                if (m.getStatistic() == Statistic.COUNT) {
                    total += (long) m.getValue();
                }
            }
        }
        return total;
    }
}



