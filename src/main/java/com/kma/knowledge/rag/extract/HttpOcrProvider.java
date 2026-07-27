package com.kma.knowledge.rag.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.kma.knowledge.config.KnowledgeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 本地 OCR HTTP 适配器。服务接收 JSON：contentBase64、mimeType，返回 JSON：text。
 */
@Component
@ConditionalOnProperty(prefix = "knowledge.ocr", name = "enabled", havingValue = "true")
public class HttpOcrProvider implements OcrProvider {
    private final KnowledgeProperties.OcrProperties properties;
    private final RestClient restClient;

    public HttpOcrProvider(KnowledgeProperties knowledgeProperties) {
        this.properties = knowledgeProperties.getOcr();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory);
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }
        this.restClient = builder.build();
    }

    @Override
    public boolean available() {
        return StringUtils.hasText(properties.getBaseUrl()) && StringUtils.hasText(properties.getEndpoint());
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/") || "application/pdf".equals(mimeType)
            || "image/*".equals(mimeType));
    }

    @Override
    public String extract(byte[] content, String mimeType) throws IOException {
        if (!supports(mimeType)) {
            throw new IOException("OCR Provider 不支持类型: " + mimeType);
        }
        try {
            JsonNode response = restClient.post()
                .uri(properties.getEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "contentBase64", Base64.getEncoder().encodeToString(content),
                    "mimeType", mimeType))
                .retrieve()
                .body(JsonNode.class);
            String text = response == null ? null : response.path("text").asText(null);
            if (!StringUtils.hasText(text)) {
                throw new IOException("OCR 服务未返回有效文本");
            }
            return text;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("OCR 服务调用失败", ex);
        }
    }
}
