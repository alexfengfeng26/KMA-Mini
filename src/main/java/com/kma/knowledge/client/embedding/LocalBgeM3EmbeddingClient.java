package com.kma.knowledge.client.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 BGE-M3 Embedding 客户端
 * <p>
 * 支持 xinference / ollama 等本地推理服务的 OpenAI 兼容 Embeddings 端点。
 * 默认地址 {@code http://localhost:9997/v1} 对应 xinference；ollama 可配为
 * {@code http://localhost:11434/v1}（需开启 OpenAI 兼容）。
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class LocalBgeM3EmbeddingClient extends HttpEmbeddingClient {

    public static final String PROVIDER = "local-bge-m3";

    public LocalBgeM3EmbeddingClient(@org.springframework.beans.factory.annotation.Qualifier("knowledgeRestClient") RestClient restClient,
                                     ObjectMapper objectMapper,
                                     KnowledgeProperties properties) {
        super(restClient, objectMapper, properties);
        log.info("LocalBgeM3EmbeddingClient 已注册，本地服务地址：{}，模型：{}",
            localConfig().getBaseUrl(), localConfig().getModel());
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public int dimension() {
        return config().getDimension();
    }

    @Override
    protected String getApiKey() {
        String key = localConfig().getApiKey();
        return key != null && !key.isEmpty() ? key : config().getApiKey();
    }

    @Override
    protected int getMaxRetry() {
        return localConfig().getMaxRetry();
    }

    @Override
    protected String buildUri() {
        return localConfig().getBaseUrl() + "/embeddings";
    }

    @Override
    protected Object buildRequestBody(List<String> texts) {
        Map<String, Object> body = new HashMap<>(2);
        body.put("model", localConfig().getModel());
        body.put("input", texts);
        return body;
    }

    @Override
    protected List<float[]> parseResponse(String response, int expectedSize) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode data = root.path("data");
        List<float[]> ApiResult = new ArrayList<>(expectedSize);
        for (JsonNode item : data) {
            JsonNode embeddingNode = item.path("embedding");
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            ApiResult.add(vector);
        }
        return ApiResult;
    }

    private KnowledgeProperties.LocalEmbeddingProperties localConfig() {
        return config().getLocal();
    }
}



