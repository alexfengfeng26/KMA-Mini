package com.kma.knowledge.client.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.config.KnowledgeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dedicated DeepSeek V4 Flash client for portal layout proposals. */
@Component
public class PortalDesignLlmClient {
    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public PortalDesignLlmClient(KnowledgeProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, buildRestClient(properties));
    }

    PortalDesignLlmClient(KnowledgeProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(KnowledgeProperties properties) {
        KnowledgeProperties.PortalDesignProperties config = properties.getPortalDesign();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(10, config.getTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));
        return RestClient.builder().requestFactory(factory).build();
    }

    public LlmChatResponse generate(List<Map<String, String>> messages, String userId) {
        KnowledgeProperties.PortalDesignProperties config = properties.getPortalDesign();
        if (!config.isEnabled() || !StringUtils.hasText(config.getApiKey())) {
            throw new KmaException(503, "AI_DESIGN_MODEL_UNAVAILABLE");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", 0.2);
        body.put("thinking", Map.of("type", "disabled"));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("max_tokens", config.getMaxOutputTokens());
        body.put("user_id", userId);
        try {
            String response = restClient.post()
                .uri(normalizedBaseUrl(config.getBaseUrl()) + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            return parse(response, config.getModel());
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String message = switch (status) {
                case 401, 403 -> "DEEPSEEK_AUTHENTICATION_FAILED";
                case 404 -> "DEEPSEEK_MODEL_NOT_FOUND";
                case 429 -> "DEEPSEEK_RATE_LIMITED";
                default -> "DEEPSEEK_REQUEST_FAILED";
            };
            throw new KmaException(status, message);
        } catch (ResourceAccessException ex) {
            throw new KmaException(504, "DEEPSEEK_REQUEST_TIMEOUT");
        } catch (KmaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new KmaException(503, "DEEPSEEK_REQUEST_FAILED");
        }
    }

    private LlmChatResponse parse(String response, String fallbackModel) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) throw new IllegalArgumentException("empty content");
            LlmChatResponse result = new LlmChatResponse();
            result.setModel(root.path("model").asText(fallbackModel));
            result.setContent(content);
            result.setFinishReason(choice.path("finish_reason").asText());
            result.setPromptTokens(root.path("usage").path("prompt_tokens").asInt(0));
            result.setCompletionTokens(root.path("usage").path("completion_tokens").asInt(0));
            return result;
        } catch (Exception ex) {
            throw new KmaException(502, "DEEPSEEK_INVALID_RESPONSE");
        }
    }

    private String normalizedBaseUrl(String value) {
        if (!StringUtils.hasText(value)) return "https://api.deepseek.com";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
