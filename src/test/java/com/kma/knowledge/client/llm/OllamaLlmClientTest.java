package com.kma.knowledge.client.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ollama LLM 客户端单元测试
 *
 * @author party
 * @date 2026/07/03
 */
class OllamaLlmClientTest {

    @Test
    void shouldParseChatResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.getLlm().setApiKey("test-key");
        properties.getLlm().getLocal().setBaseUrl("http://localhost:11434/v1");
        properties.getLlm().getLocal().setModel("qwen2.5");
        properties.getLlm().getLocal().setApiKey("ollama");

        String responseJson = "{\"model\":\"qwen2.5\",\"choices\":["
                + "{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"},\"finish_reason\":\"stop\"}"
                + "],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2}}";

        server.expect(MockRestRequestMatchers.requestTo("http://localhost:11434/v1/chat/completions"))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer ollama"))
                .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(MockRestResponseCreators.withSuccess(responseJson, MediaType.APPLICATION_JSON));

        OllamaLlmClient client = new OllamaLlmClient(restClient, new ObjectMapper(), properties);

        LlmChatRequest request = new LlmChatRequest();
        request.setMessages(List.of(
            Map.of("role", "system", "content", "你是党建助手"),
            Map.of("role", "user", "content", "你好")
        ));

        LlmChatResponse response = client.chat(request);

        assertNotNull(response);
        assertEquals("你好", response.getContent());
        assertEquals(10, response.getPromptTokens());
        assertEquals(2, response.getCompletionTokens());
        server.verify();
    }

    @Test
    void shouldReturnProvider() {
        KnowledgeProperties properties = new KnowledgeProperties();
        OllamaLlmClient client = new OllamaLlmClient(null, new ObjectMapper(), properties);

        assertEquals("ollama", client.provider());
    }

    @Test
    void shouldPingLocalProviderUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getLlm().setBaseUrl("https://api.deepseek.com/v1");
        properties.getLlm().getLocal().setBaseUrl("http://localhost:11434/v1");
        server.expect(MockRestRequestMatchers.requestTo("http://localhost:11434/v1"))
            .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.HEAD))
            .andRespond(MockRestResponseCreators.withSuccess());

        OllamaLlmClient client = new OllamaLlmClient(builder.build(), new ObjectMapper(), properties);

        assertTrue(client.ping());
        server.verify();
    }
}



