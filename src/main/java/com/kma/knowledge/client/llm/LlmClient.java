package com.kma.knowledge.client.llm;

import java.util.function.Consumer;

/**
 * 大语言模型客户端抽象
 *
 * @author party
 * @date 2026/06/30
 */
public interface LlmClient {

    /**
     * 提供商编码
     */
    String provider();

    /**
     * 非流式对话
     */
    LlmChatResponse chat(LlmChatRequest request);

    /**
     * 流式对话。未提供原生流式能力的客户端自动降级为一次性返回完整内容。
     */
    default void streamChat(LlmChatRequest request, Consumer<String> onChunk) {
        LlmChatResponse response = chat(request);
        if (response != null && response.getContent() != null) {
            onChunk.accept(response.getContent());
        }
    }

    /**
     * 轻量连通性探测：默认返回 true，HTTP 客户端可覆盖为实际网络探测
     */
    default boolean ping() {
        return true;
    }
}



