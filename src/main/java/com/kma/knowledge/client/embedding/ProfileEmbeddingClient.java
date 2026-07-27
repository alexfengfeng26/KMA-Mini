package com.kma.knowledge.client.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.model.ResolvedModelProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAI-compatible embedding client created from the latest persisted Profile. */
final class ProfileEmbeddingClient implements EmbeddingClient {
    private final ResolvedModelProfile profile;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    ProfileEmbeddingClient(ResolvedModelProfile profile, ObjectMapper objectMapper) {
        this.profile = profile;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(10, profile.getTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(profile.getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override public String provider() { return profile.getProfileCode(); }
    @Override public int dimension() { return profile.getDimension(); }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (!StringUtils.hasText(profile.getBaseUrl())) {
            throw new KmaException(503, "Embedding Profile 未配置 baseUrl: " + profile.getProfileCode());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", profile.getModelName());
        body.put("input", texts);
        RestClient.RequestBodySpec request = restClient.post()
            .uri(profile.getBaseUrl() + "/embeddings")
            .contentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(profile.getSecret())) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + profile.getSecret());
        }
        String response = request.body(body).retrieve().body(String.class);
        try {
            JsonNode data = objectMapper.readTree(response).path("data");
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode values = item.path("embedding");
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) vector[i] = (float) values.get(i).asDouble();
                if (vector.length != dimension()) {
                    throw new KmaException(502, "Embedding 返回维度不匹配: " + vector.length);
                }
                vectors.add(vector);
            }
            if (vectors.size() != texts.size()) throw new KmaException(502, "Embedding 返回数量不匹配");
            return vectors;
        } catch (KmaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new KmaException("Embedding 响应解析失败", ex);
        }
    }
}
