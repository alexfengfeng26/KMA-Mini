package com.kma.knowledge.client.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek LLM 客户端
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DeepSeekLlmClient extends HttpLlmClient {

    public static final String PROVIDER = "deepseek";

    public DeepSeekLlmClient(@org.springframework.beans.factory.annotation.Qualifier("knowledgeRestClient") RestClient restClient,
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
        return config().getBaseUrl() + "/chat/completions";
    }

    @Override
    protected Map<String, Object> buildRequestBody(LlmChatRequest request) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("model", request.getModel() != null ? request.getModel() : config().getModel());
        body.put("messages", request.getMessages());
        body.put("stream", request.isStream());
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        return body;
    }

    @Override
    protected LlmChatResponse parseResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        LlmChatResponse result = new LlmChatResponse();
        result.setModel(root.path("model").asText());

        JsonNode choice = root.path("choices").get(0);
        if (choice != null) {
            result.setContent(choice.path("message").path("content").asText());
            result.setFinishReason(choice.path("finish_reason").asText());
        }

        JsonNode usage = root.path("usage");
        if (usage != null) {
            result.setPromptTokens(usage.path("prompt_tokens").asInt(0));
            result.setCompletionTokens(usage.path("completion_tokens").asInt(0));
        }
        return result;
    }

    /**
     * 流式对话：通过 SSE 逐段返回内容
     */
    @Override
    public void streamChat(LlmChatRequest request, Consumer<String> onChunk) {
        String apiKey = config().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("DeepSeek API Key 未配置：knowledge.llm.apiKey");
        }

        Map<String, Object> body = buildRequestBody(request);
        body.put("stream", true);

        restClient.post()
            .uri(buildUri())
            .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .exchange((req, response) -> {
                readStream(response, onChunk);
                return null;
            });
    }

    private void readStream(ClientHttpResponse response, Consumer<String> onChunk) throws java.io.IOException {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode root = objectMapper.readTree(data);
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode delta = choices.get(0).path("delta");
                    if (delta.has("content")) {
                        String content = delta.path("content").asText();
                        if (content != null && !content.isEmpty()) {
                            onChunk.accept(content);
                        }
                    }
                }
            }
        }
    }
}



