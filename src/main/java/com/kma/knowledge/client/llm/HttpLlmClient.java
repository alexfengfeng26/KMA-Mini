package com.kma.knowledge.client.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * LLM 客户端 HTTP 抽象基类
 * <p>
 * 封装 RestClient 注入、指数退避重试、通用 POST 请求；子类只需实现请求体构造和响应解析。
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
public abstract class HttpLlmClient implements LlmClient {

    protected final RestClient restClient;
    protected final ObjectMapper objectMapper;
    protected final KnowledgeProperties properties;

    protected HttpLlmClient(RestClient restClient,
                            ObjectMapper objectMapper,
                            KnowledgeProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 当前客户端使用的配置
     */
    protected KnowledgeProperties.LlmProperties config() {
        return properties.getLlm();
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(provider() + " API Key 未配置：knowledge.llm.apiKey");
        }

        int maxRetry = Math.max(1, getMaxRetry());
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                String response = restClient.post()
                    .uri(buildUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(request))
                    .retrieve()
                    .body(String.class);
                return parseResponse(response);
            } catch (Exception e) {
                lastException = e;
                log.warn("{} 调用失败（第 {} 次重试）: {}", provider(), attempt, e.getMessage());
                if (attempt < maxRetry) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw new RuntimeException(provider() + " 调用失败，已重试 " + maxRetry + " 次", lastException);
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
    protected abstract Map<String, Object> buildRequestBody(LlmChatRequest request);

    /**
     * 解析响应
     */
    protected abstract LlmChatResponse parseResponse(String response) throws Exception;

    protected void sleepBackoff(int attempt) {
        try {
            Thread.sleep((long) Math.pow(2, attempt) * 500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean ping() {
        try {
            return Boolean.TRUE.equals(restClient.head()
                .uri(healthUri())
                .exchange((req, res) -> {
                    int status = res.getStatusCode().value();
                    // 401 表示服务可达但认证失败，仍视为网络层可用
                    return status < 500 && (status >= 200 || status == 401);
                }));
        } catch (Exception e) {
            log.warn("{} LLM 服务连通性探测失败: {}", provider(), e.getMessage());
            return false;
        }
    }

    /** 当前提供商的健康探测地址。 */
    protected String healthUri() {
        return config().getBaseUrl();
    }
}



