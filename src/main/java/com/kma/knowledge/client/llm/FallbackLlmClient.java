package com.kma.knowledge.client.llm;

import com.kma.common.exception.KmaException;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LLM 主备自动降级客户端
 *
 * @author party
 * @date 2026/07/02
 */
@Slf4j
public class FallbackLlmClient implements LlmClient {

    private final String primary;
    private final List<String> chain;
    private final Map<String, LlmClient> clientMap;
    private final RagMetricsRecorder metricsRecorder;

    public FallbackLlmClient(String primary,
                             List<String> chain,
                             Map<String, LlmClient> clientMap,
                             RagMetricsRecorder metricsRecorder) {
        this.primary = primary;
        this.chain = chain;
        this.clientMap = clientMap;
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    public String provider() {
        return primary;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        Exception lastException = null;
        for (int i = 0; i < chain.size(); i++) {
            String provider = chain.get(i);
            LlmClient client = clientMap.get(provider);
            if (client == null) {
                log.warn("LLM 提供商未注册, provider={}", provider);
                continue;
            }
            try {
                LlmChatResponse response = client.chat(request);
                if (i > 0 && metricsRecorder != null) {
                    metricsRecorder.recordModelFallback("llm", primary, provider);
                    log.info("LLM 已降级到备用提供商, primary={}, fallback={}", primary, provider);
                }
                return response;
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM 调用失败, provider={}, error={}", provider, e.getMessage());
            }
        }
        throw new KmaException("所有 LLM 提供商均不可用: " + chain, lastException);
    }

    @Override
    public void streamChat(LlmChatRequest request, Consumer<String> onChunk) {
        Exception lastException = null;
        AtomicBoolean emitted = new AtomicBoolean(false);
        for (int i = 0; i < chain.size(); i++) {
            String provider = chain.get(i);
            LlmClient client = clientMap.get(provider);
            if (client == null) {
                log.warn("LLM 提供商未注册, provider={}", provider);
                continue;
            }
            try {
                client.streamChat(request, chunk -> {
                    emitted.set(true);
                    onChunk.accept(chunk);
                });
                if (i > 0 && metricsRecorder != null) {
                    metricsRecorder.recordModelFallback("llm-stream", primary, provider);
                    log.info("流式 LLM 已降级到备用提供商, primary={}, fallback={}", primary, provider);
                }
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("流式 LLM 调用失败, provider={}, error={}", provider, e.getMessage());
                if (emitted.get()) {
                    throw new KmaException("流式输出已开始，禁止切换模型以避免混合回答", e);
                }
            }
        }
        throw new KmaException("所有流式 LLM 提供商均不可用: " + chain, lastException);
    }

    @Override
    public boolean ping() {
        for (String provider : chain) {
            LlmClient client = clientMap.get(provider);
            if (client == null) {
                continue;
            }
            try {
                if (client.ping()) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("LLM 连通性探测失败, provider={}", provider, e);
            }
        }
        return false;
    }
}



