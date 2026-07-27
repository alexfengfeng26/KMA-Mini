package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 调用日志视图对象
 *
 * @author party
 * @date 2026/07/02
 */
@Data
@Schema(name = "KnowledgeCallLogVO", description = "KnowledgeCallLogVO 数据模型")
public class KnowledgeCallLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long logId;

    private Long userId;

    private String username;

    private String spaceCode;

    private String ragMode;

    private String query;

    private Integer topK;

    private String sourceTags;

    private Integer hitCount;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer costMillis;

    private String llmModel;

    private String status;

    private String errorMessage;

    private String securityFlags;

    private LocalDateTime createTime;
}



