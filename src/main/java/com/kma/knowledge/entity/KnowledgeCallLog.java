package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 调用日志
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@TableName("knowledge_call_log")
public class KnowledgeCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
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



