package com.kma.knowledge.client.llm;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * LLM 对话请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "LlmChatRequest", description = "LlmChatRequest 数据模型")
public class LlmChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String model;

    private List<Map<String, String>> messages;

    private boolean stream;

    private Double temperature;
}



