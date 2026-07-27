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
 * 智谱 Embedding 客户端（embedding-2，1024 维）
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ZhipuEmbeddingClient extends HttpEmbeddingClient {

    public static final String PROVIDER = "zhipu";

    public ZhipuEmbeddingClient(@org.springframework.beans.factory.annotation.Qualifier("knowledgeRestClient") RestClient restClient,
                                ObjectMapper objectMapper,
                                KnowledgeProperties properties) {
        super(restClient, objectMapper, properties);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    protected String buildUri() {
        return config().getBaseUrl() + "/embeddings";
    }

    @Override
    protected Object buildRequestBody(List<String> texts) {
        Map<String, Object> body = new HashMap<>(2);
        body.put("model", config().getModel());
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
}



