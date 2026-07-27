package com.kma.knowledge.client.llm;

import lombok.Data;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * LLM 对话响应
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "LlmChatResponse", description = "LlmChatResponse 数据模型")
public class LlmChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String content;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private String finishReason;
}



