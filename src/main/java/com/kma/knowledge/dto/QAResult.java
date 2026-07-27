package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 问答结果
 *
 * @author party
 * @date 2026/06/30
 */
@Data
public class QAResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String answer;

    /** Whether the answer was grounded in retrieved evidence. */
    private Boolean answered;

    /** Stable refusal/degradation reason, for example NO_EVIDENCE. */
    private String reason;

    private List<ChunkHitVO> citations;

    private Integer promptTokens;

    private Integer completionTokens;

    private String llmModel;

    private Long sessionId;
}



