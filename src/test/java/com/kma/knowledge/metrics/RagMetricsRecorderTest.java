package com.kma.knowledge.metrics;

import com.kma.knowledge.service.impl.KnowledgeMetricsServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagMetricsRecorderTest {

    @Test
    void recordsEveryRagMetricAndBuildsFilteredDashboard() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RagMetricsRecorder recorder = new RagMetricsRecorder(registry);

        recorder.recordRetrieveLatency("party", 120);
        recorder.recordRetrieveHitCount("party", 3);
        recorder.recordRetrieveStageLatency("party", "vector", 35);
        recorder.recordQaLatency("party", 300);
        recorder.recordQaTokens("party", "qwen", 20, 8);
        recorder.recordQaTokens("party", "qwen", 0, 0);
        recorder.recordQaStatus("party", "success");
        recorder.recordQaStatus("party", "failed");
        recorder.recordLlmLatency("local", "qwen", 180);
        recorder.recordEmbeddingLatency("local", "bge-m3", 45);
        recorder.recordEmbeddingTexts("local", "bge-m3", 4);
        recorder.recordIngestStatus("party", "completed");
        recorder.recordIngestStatus("party", "failed");
        recorder.recordIngestLatency("party", 500);
        recorder.pendingIngestGauge("party").set(2);
        recorder.recordModelFallback("llm", "qwen", "deepseek");

        Map<String, Object> dashboard = dashboard(
            new KnowledgeMetricsServiceImpl(registry, mockJdbc()), "party");
        assertThat(dashboard).containsEntry("qaTotal", 2L)
            .containsEntry("qaSuccess", 1L)
            .containsEntry("qaFailed", 1L)
            .containsEntry("promptTokens", 20L)
            .containsEntry("completionTokens", 8L)
            .containsEntry("totalTokens", 28L)
            .containsEntry("ingestCompleted", 1L)
            .containsEntry("ingestFailed", 1L);
        assertThat((double) dashboard.get("qaAvgLatency")).isPositive();
        assertThat((double) dashboard.get("retrieveAvgLatency")).isPositive();
        assertThat((double) dashboard.get("llmAvgLatency")).isPositive();
        assertThat((double) dashboard.get("embeddingAvgLatency")).isPositive();
        assertThat(registry.find("knowledge.ingest.pending").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void normalizesMissingTagsAndReturnsZeroDashboardForUnknownSpace() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RagMetricsRecorder recorder = new RagMetricsRecorder(registry);
        recorder.recordQaStatus(null, "failed");
        recorder.recordLlmLatency(null, "", 1);
        recorder.recordEmbeddingLatency(null, null, 1);
        recorder.recordModelFallback(null, null, null);

        assertThat(registry.find("knowledge.qa.status").tag("space_code", "unknown").counter()).isNotNull();
        assertThat(dashboard(new KnowledgeMetricsServiceImpl(registry, mockJdbc()), "missing"))
            .containsEntry("qaTotal", 0L)
            .containsEntry("qaAvgLatency", 0.0)
            .containsEntry("totalTokens", 0L);
    }

    private JdbcTemplate mockJdbc() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(Long.class),
            org.mockito.ArgumentMatchers.<Object[]>any()
        )).thenReturn(0L);
        return jdbc;
    }

    private Map<String, Object> dashboard(KnowledgeMetricsServiceImpl service, String spaceCode) {
        return service.getDashboardMetrics(spaceCode);
    }
}
