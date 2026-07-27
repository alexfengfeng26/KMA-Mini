package com.kma.knowledge.client.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackLlmClientStreamTest {

    @Test
    void shouldFallbackWhenPrimaryStreamFails() {
        LlmClient primary = new StubClient("primary", true, null);
        LlmClient fallback = new StubClient("fallback", false, "备用回答");
        FallbackLlmClient client = new FallbackLlmClient(
            "primary", List.of("primary", "fallback"),
            Map.of("primary", primary, "fallback", fallback), null);
        StringBuilder chunks = new StringBuilder();

        client.streamChat(new LlmChatRequest(), chunks::append);

        assertThat(chunks).hasToString("备用回答");
    }

    @Test
    void shouldNotMixProvidersAfterFirstChunkWasEmitted() {
        LlmClient primary = new LlmClient() {
            @Override public String provider() { return "primary"; }
            @Override public LlmChatResponse chat(LlmChatRequest request) { throw new UnsupportedOperationException(); }
            @Override public void streamChat(LlmChatRequest request, java.util.function.Consumer<String> onChunk) {
                onChunk.accept("部分回答");
                throw new IllegalStateException("connection lost");
            }
        };
        LlmClient fallback = new StubClient("fallback", false, "不应拼接");
        FallbackLlmClient client = new FallbackLlmClient(
            "primary", List.of("primary", "fallback"),
            Map.of("primary", primary, "fallback", fallback), null);
        StringBuilder chunks = new StringBuilder();

        assertThatThrownBy(() -> client.streamChat(new LlmChatRequest(), chunks::append))
            .hasMessageContaining("禁止切换模型");
        assertThat(chunks).hasToString("部分回答");
    }

    private record StubClient(String provider, boolean fail, String answer) implements LlmClient {
        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            if (fail) {
                throw new IllegalStateException("unavailable");
            }
            LlmChatResponse response = new LlmChatResponse();
            response.setContent(answer);
            return response;
        }
    }
}
