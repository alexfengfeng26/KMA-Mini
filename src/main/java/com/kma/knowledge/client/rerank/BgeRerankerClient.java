package com.kma.knowledge.client.rerank;

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
 * BGE 重排序客户端
 * <p>
 * 配置服务地址时调用远程 Reranker 服务；未配置时退化为简单打分，保证链路可用。
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class BgeRerankerClient extends HttpRerankClient {

    public static final String PROVIDER = "bge-reranker-base";

    public BgeRerankerClient(@org.springframework.beans.factory.annotation.Qualifier("knowledgeRestClient") RestClient restClient,
                             ObjectMapper objectMapper,
                             KnowledgeProperties properties) {
        super(restClient, objectMapper, properties);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    protected String buildUri(String baseUrl) {
        return baseUrl + "/rerank";
    }

    @Override
    protected Map<String, Object> buildRequestBody(String query, List<String> texts) {
        Map<String, Object> body = new HashMap<>(3);
        body.put("query", query);
        body.put("texts", texts);
        body.put("model", config().getDefaultProvider());
        return body;
    }

    @Override
    protected List<Double> parseResponse(String response, int expectedSize) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode results = root.path("results");
        List<Double> scores = new ArrayList<>(expectedSize);
        for (JsonNode item : results) {
            scores.add(item.path("score").asDouble(0.0));
        }
        return scores;
    }
}



