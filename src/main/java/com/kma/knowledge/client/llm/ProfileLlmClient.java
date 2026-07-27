package com.kma.knowledge.client.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.model.ResolvedModelProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** OpenAI-compatible chat client created from a runtime Profile. */
final class ProfileLlmClient implements LlmClient {
    private final ResolvedModelProfile profile;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    ProfileLlmClient(ResolvedModelProfile profile, ObjectMapper objectMapper) {
        this.profile = profile;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(10, profile.getTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(profile.getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override public String provider() { return profile.getProfileCode(); }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        String response = request(false, request).retrieve().body(String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choice = root.path("choices").path(0);
            LlmChatResponse result = new LlmChatResponse();
            result.setModel(root.path("model").asText(profile.getModelName()));
            result.setContent(choice.path("message").path("content").asText());
            result.setFinishReason(choice.path("finish_reason").asText());
            result.setPromptTokens(root.path("usage").path("prompt_tokens").asInt(0));
            result.setCompletionTokens(root.path("usage").path("completion_tokens").asInt(0));
            return result;
        } catch (Exception ex) {
            throw new KmaException("LLM 响应解析失败", ex);
        }
    }

    @Override
    public void streamChat(LlmChatRequest request, Consumer<String> onChunk) {
        request(true, request).exchange((req, response) -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    JsonNode delta = objectMapper.readTree(data).path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && StringUtils.hasText(delta.asText())) onChunk.accept(delta.asText());
                }
            }
            return null;
        });
    }

    private RestClient.RequestBodySpec request(boolean stream, LlmChatRequest input) {
        if (!StringUtils.hasText(profile.getBaseUrl())) {
            throw new KmaException(503, "LLM Profile 未配置 baseUrl: " + profile.getProfileCode());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", profile.getModelName());
        body.put("messages", input.getMessages());
        body.put("stream", stream);
        if (input.getTemperature() != null) body.put("temperature", input.getTemperature());
        RestClient.RequestBodySpec request = restClient.post()
            .uri(profile.getBaseUrl() + "/chat/completions")
            .contentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(profile.getSecret())) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + profile.getSecret());
        }
        return request.body(body);
    }
}
