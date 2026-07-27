package com.kma.knowledge.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG 链路指标记录器
 *
 * @author party
 * @date 2026/07/02
 */
@Slf4j
@Component
public class RagMetricsRecorder {

    private final MeterRegistry meterRegistry;

    public RagMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录检索耗时
     */
    public void recordRetrieveLatency(String spaceCode, long millis) {
        Timer.builder("knowledge.retrieve.latency")
            .description("检索链路总耗时")
            .tag("space_code", normalize(spaceCode))
            .register(meterRegistry)
            .record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录检索命中率（最终命中数）
     */
    public void recordRetrieveHitCount(String spaceCode, int hitCount) {
        meterRegistry.summary("knowledge.retrieve.hit.count",
                "space_code", normalize(spaceCode))
            .record(hitCount);
    }

    /**
     * 记录检索分阶段耗时
     */
    public void recordRetrieveStageLatency(String spaceCode, String stage, long millis) {
        Timer.builder("knowledge.retrieve.stage.latency")
            .description("检索分阶段耗时")
            .tag("space_code", normalize(spaceCode))
            .tag("stage", stage)
            .register(meterRegistry)
            .record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 QA 总耗时
     */
    public void recordQaLatency(String spaceCode, long millis) {
        Timer.builder("knowledge.qa.latency")
            .description("问答链路总耗时")
            .tag("space_code", normalize(spaceCode))
            .register(meterRegistry)
            .record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 QA Token 消耗
     */
    public void recordQaTokens(String spaceCode, String llmModel, int promptTokens, int completionTokens) {
        if (promptTokens > 0) {
            meterRegistry.counter("knowledge.qa.tokens",
                    "space_code", normalize(spaceCode),
                    "llm_model", normalize(llmModel),
                    "type", "prompt")
                .increment(promptTokens);
        }
        if (completionTokens > 0) {
            meterRegistry.counter("knowledge.qa.tokens",
                    "space_code", normalize(spaceCode),
                    "llm_model", normalize(llmModel),
                    "type", "completion")
                .increment(completionTokens);
        }
    }

    /**
     * 记录 QA 结果状态
     */
    public void recordQaStatus(String spaceCode, String status) {
        Counter.builder("knowledge.qa.status")
            .description("问答状态计数")
            .tag("space_code", normalize(spaceCode))
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    /**
     * 记录 LLM 调用耗时
     */
    public void recordLlmLatency(String provider, String model, long millis) {
        Timer.builder("knowledge.llm.latency")
            .description("LLM 调用耗时")
            .tag("provider", normalize(provider))
            .tag("model", normalize(model))
            .register(meterRegistry)
            .record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 Embedding 调用耗时
     */
    public void recordEmbeddingLatency(String provider, String model, long millis) {
        Timer.builder("knowledge.embedding.latency")
            .description("Embedding 调用耗时")
            .tag("provider", normalize(provider))
            .tag("model", normalize(model))
            .register(meterRegistry)
            .record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 Embedding 调用 token/文本量
     */
    public void recordEmbeddingTexts(String provider, String model, int textCount) {
        Counter.builder("knowledge.embedding.texts")
            .description("Embedding 处理文本段数")
            .tag("provider", normalize(provider))
            .tag("model", normalize(model))
            .register(meterRegistry)
            .increment(textCount);
    }

    /**
     * 记录摄入状态
     */
    public void recordIngestStatus(String spaceCode, String status) {
        Counter.builder("knowledge.ingest.status")
            .description("文档摄入状态计数")
            .tag("space_code", normalize(spaceCode))
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    /**
     * 记录摄入耗时
     */
    public void recordIngestLatency(String spaceCode, long millis) {
        Timer.builder("knowledge.ingest.latency")
            .description("文档摄入耗时")
            .tag("space_code", normalize(spaceCode))
            .register(meterRegistry)
            .record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * 创建并跟踪当前处理中的文档数（可用于告警背压）
     */
    public AtomicInteger pendingIngestGauge(String spaceCode) {
        AtomicInteger gauge = new AtomicInteger(0);
        meterRegistry.gauge("knowledge.ingest.pending",
            Tags.of("space_code", normalize(spaceCode)), gauge);
        return gauge;
    }

    /**
     * 记录模型降级切换
     */
    public void recordModelFallback(String modelType, String primary, String fallback) {
        Counter.builder("knowledge.model.fallback")
            .description("模型主备切换次数")
            .tag("model_type", normalize(modelType))
            .tag("primary", normalize(primary))
            .tag("fallback", normalize(fallback))
            .register(meterRegistry)
            .increment();
    }

    private String normalize(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }
}



