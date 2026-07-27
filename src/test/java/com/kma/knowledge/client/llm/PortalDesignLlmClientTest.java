package com.kma.knowledge.client.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;

class PortalDesignLlmClientTest {
    @Test
    void sendsDeepSeekFlashStructuredNonThinkingRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getPortalDesign().setApiKey("secret-for-test");
        properties.getPortalDesign().setModel("deepseek-v4-flash");
        properties.getPortalDesign().setBaseUrl("https://api.deepseek.com");
        String response = """
            {"model":"deepseek-v4-flash","choices":[{"message":{"content":"{\\"summary\\":\\"ok\\",\\"target\\":{}}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":12,"completion_tokens":8}}
            """;
        server.expect(MockRestRequestMatchers.requestTo("https://api.deepseek.com/chat/completions"))
            .andExpect(header("Authorization", "Bearer secret-for-test"))
            .andExpect(content().string(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("\"model\":\"deepseek-v4-flash\""),
                org.hamcrest.Matchers.containsString("\"thinking\":{\"type\":\"disabled\"}"),
                org.hamcrest.Matchers.containsString("\"response_format\":{\"type\":\"json_object\"}"),
                org.hamcrest.Matchers.containsString("\"max_tokens\":16384"))))
            .andRespond(MockRestResponseCreators.withSuccess(response, MediaType.APPLICATION_JSON));

        PortalDesignLlmClient client = new PortalDesignLlmClient(
            properties, new ObjectMapper(), builder.build());
        LlmChatResponse result = client.generate(
            List.of(Map.of("role", "user", "content", "design")), "kma-1");

        assertThat(result.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(result.getContent()).contains("\"target\"");
        assertThat(result.getPromptTokens()).isEqualTo(12);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
        "UNAUTHORIZED,DEEPSEEK_AUTHENTICATION_FAILED",
        "NOT_FOUND,DEEPSEEK_MODEL_NOT_FOUND",
        "TOO_MANY_REQUESTS,DEEPSEEK_RATE_LIMITED"
    })
    void mapsActionableDeepSeekErrors(HttpStatus status, String expectedMessage) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties properties = configuredProperties();
        server.expect(MockRestRequestMatchers.requestTo("https://api.deepseek.com/chat/completions"))
            .andRespond(MockRestResponseCreators.withStatus(status));
        PortalDesignLlmClient client = new PortalDesignLlmClient(
            properties, new ObjectMapper(), builder.build());

        assertThatThrownBy(() -> client.generate(
            List.of(Map.of("role", "user", "content", "design")), "kma-1"))
            .isInstanceOf(KmaException.class)
            .hasMessage(expectedMessage);
        server.verify();
    }

    @Test
    void mapsTimeoutWithoutLeakingRequestDetails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties properties = configuredProperties();
        server.expect(MockRestRequestMatchers.requestTo("https://api.deepseek.com/chat/completions"))
            .andRespond(request -> {
                throw new SocketTimeoutException("test timeout");
            });
        PortalDesignLlmClient client = new PortalDesignLlmClient(
            properties, new ObjectMapper(), builder.build());

        assertThatThrownBy(() -> client.generate(
            List.of(Map.of("role", "user", "content", "design")), "kma-1"))
            .isInstanceOf(KmaException.class)
            .hasMessage("DEEPSEEK_REQUEST_TIMEOUT");
    }

    @Test
    void rejectsInvalidJsonResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties properties = configuredProperties();
        server.expect(MockRestRequestMatchers.requestTo("https://api.deepseek.com/chat/completions"))
            .andRespond(MockRestResponseCreators.withSuccess("not-json", MediaType.APPLICATION_JSON));
        PortalDesignLlmClient client = new PortalDesignLlmClient(
            properties, new ObjectMapper(), builder.build());

        assertThatThrownBy(() -> client.generate(
            List.of(Map.of("role", "user", "content", "design")), "kma-1"))
            .isInstanceOf(KmaException.class)
            .hasMessage("DEEPSEEK_INVALID_RESPONSE");
    }

    private KnowledgeProperties configuredProperties() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getPortalDesign().setApiKey("secret-for-test");
        properties.getPortalDesign().setBaseUrl("https://api.deepseek.com");
        return properties;
    }
}
