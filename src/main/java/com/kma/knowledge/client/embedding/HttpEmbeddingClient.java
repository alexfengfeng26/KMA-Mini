package com.kma.knowledge.client.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Embedding 客户端 HTTP 抽象基类
 * <p>
 * 封装 RestClient 注入、指数退避重试、通用 POST 请求；子类只需实现请求体构造和响应解析。
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
public abstract class HttpEmbeddingClient implements EmbeddingClient {

    protected final RestClient restClient;
    protected final ObjectMapper objectMapper;
    protected final KnowledgeProperties properties;

    protected HttpEmbeddingClient(RestClient restClient,
                                  ObjectMapper objectMapper,
                                  KnowledgeProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 当前客户端使用的配置
     */
    protected KnowledgeProperties.EmbeddingProperties config() {
        return properties.getEmbedding();
    }

    @Override
    public int dimension() {
        return config().getDimension();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(provider() + " Embedding API Key 未配置");
        }

        int maxRetry = Math.max(1, getMaxRetry());
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                String response = restClient.post()
                    .uri(buildUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(texts))
                    .retrieve()
                    .body(String.class);
                return parseResponse(response, texts.size());
            } catch (Exception e) {
                lastException = e;
                log.warn("{} Embedding 调用失败（第 {} 次重试）: {}", provider(), attempt, e.getMessage());
                if (attempt < maxRetry) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw new RuntimeException(provider() + " Embedding 调用失败，已重试 " + maxRetry + " 次", lastException);
    }

    /**
     * 当前客户端使用的 API Key，子类可覆盖以使用独立配置
     */
    protected String getApiKey() {
        return config().getApiKey();
    }

    /**
     * 当前客户端使用的最大重试次数，子类可覆盖以使用独立配置
     */
    protected int getMaxRetry() {
        return config().getMaxRetry();
    }

    /**
     * 构造请求 URI
     */
    protected abstract String buildUri();

    /**
     * 构造请求体
     */
    protected abstract Object buildRequestBody(List<String> texts);

    /**
     * 解析响应为向量列表
     */
    protected abstract List<float[]> parseResponse(String response, int expectedSize) throws Exception;

    protected void sleepBackoff(int attempt) {
        try {
            Thread.sleep((long) Math.pow(2, attempt) * 500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}



