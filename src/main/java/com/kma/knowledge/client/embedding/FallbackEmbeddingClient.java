package com.kma.knowledge.client.embedding;

import com.kma.common.exception.KmaException;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embedding 主备自动降级客户端
 *
 * @author party
 * @date 2026/07/02
 */
@Slf4j
public class FallbackEmbeddingClient implements EmbeddingClient {

    private final String primary;
    private final List<String> chain;
    private final Map<String, EmbeddingClient> clientMap;
    private final RagMetricsRecorder metricsRecorder;

    public FallbackEmbeddingClient(String primary,
                                   List<String> chain,
                                   Map<String, EmbeddingClient> clientMap,
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
    public int dimension() {
        EmbeddingClient client = clientMap.get(primary);
        return client != null ? client.dimension() : 0;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        Exception lastException = null;
        for (int i = 0; i < chain.size(); i++) {
            String provider = chain.get(i);
            EmbeddingClient client = clientMap.get(provider);
            if (client == null) {
                log.warn("Embedding 提供商未注册, provider={}", provider);
                continue;
            }
            try {
                List<float[]> ApiResult = client.embed(texts);
                if (i > 0 && metricsRecorder != null) {
                    metricsRecorder.recordModelFallback("embedding", primary, provider);
                    log.info("Embedding 已降级到备用提供商, primary={}, fallback={}", primary, provider);
                }
                return ApiResult;
            } catch (Exception e) {
                lastException = e;
                log.warn("Embedding 调用失败, provider={}, error={}", provider, e.getMessage());
            }
        }
        throw new KmaException("所有 Embedding 提供商均不可用: " + chain, lastException);
    }

    @Override
    public boolean ping() {
        for (String provider : chain) {
            EmbeddingClient client = clientMap.get(provider);
            if (client == null) {
                continue;
            }
            try {
                if (client.ping()) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Embedding 连通性探测失败, provider={}", provider, e);
            }
        }
        return false;
    }
}



