package com.kma.knowledge.client.rerank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.rag.retrieve.LexicalQueryAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rerank 客户端 HTTP 抽象基类
 * <p>
 * 封装 RestClient 注入、通用 POST 请求；子类只需实现请求体构造和响应解析。
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
public abstract class HttpRerankClient implements RerankClient {

    protected final RestClient restClient;
    protected final ObjectMapper objectMapper;
    protected final KnowledgeProperties properties;

    protected HttpRerankClient(RestClient restClient,
                               ObjectMapper objectMapper,
                               KnowledgeProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 当前客户端使用的配置
     */
    protected KnowledgeProperties.RerankProperties config() {
        return properties.getRerank();
    }

    @Override
    public List<Double> score(String query, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String baseUrl = config().getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            return fallbackScore(query, texts);
        }
        try {
            String response = doRemoteScore(query, texts, baseUrl);
            return parseResponse(response, texts.size());
        } catch (Exception e) {
            log.warn("{} 远程 Reranker 调用失败，退化到兜底打分: {}", provider(), e.getMessage());
            return fallbackScore(query, texts);
        }
    }

    protected String doRemoteScore(String query, List<String> texts, String baseUrl) {
        String apiKey = config().getApiKey();
        RestClient.RequestBodySpec spec = restClient.post()
            .uri(buildUri(baseUrl))
            .contentType(MediaType.APPLICATION_JSON)
            .body(buildRequestBody(query, texts));
        if (apiKey != null && !apiKey.isEmpty()) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return spec.retrieve().body(String.class);
    }

    /**
     * 构造完整请求 URI
     */
    protected abstract String buildUri(String baseUrl);

    /**
     * 构造请求体
     */
    protected abstract Map<String, Object> buildRequestBody(String query, List<String> texts);

    /**
     * 解析响应为分数列表
     */
    protected abstract List<Double> parseResponse(String response, int expectedSize) throws Exception;

    /**
     * 远程服务不可用时兜底打分：关键词覆盖度
     */
    protected List<Double> fallbackScore(String query, List<String> texts) {
        Set<String> queryTerms = new LinkedHashSet<>(LexicalQueryAnalyzer.tokenizeValue(query));
        return texts.stream().map(text -> {
            Set<String> textTerms = new LinkedHashSet<>(LexicalQueryAnalyzer.tokenizeValue(text));
            int match = 0;
            for (String term : queryTerms) {
                if (textTerms.contains(term)) {
                    match++;
                }
            }
            double score = queryTerms.isEmpty() ? 0.0 : (double) match / queryTerms.size();
            return Math.min(1.0, score + 0.3);
        }).toList();
    }
}



