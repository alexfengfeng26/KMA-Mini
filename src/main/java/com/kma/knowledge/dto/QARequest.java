package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 问答请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "QARequest", description = "QARequest 数据模型")
public class QARequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "查询语句不能为空")
    @Size(max = 2000, message = "查询语句长度不能超过 2000")
    private String query;

    @NotBlank(message = "空间编码不能为空")
    @Size(max = 64, message = "空间编码长度不能超过 64")
    private String spaceCode;

    /**
     * 来源标签过滤
     */
    @Size(max = 20)
    private List<@Size(max = 64) String> sourceTags;

    @Min(1) @Max(100)
    private Integer topK;

    /**
     * 是否流式输出（一期默认 false）
     */
    private Boolean stream;

    /**
     * 会话 ID（二期多轮上下文）
     */
    private Long sessionId;

    /** Portal scope selectors. The portal controller always forces portalOnly=true. */
    private Boolean portalOnly;

    @Size(max = 5)
    private List<@Size(max = 32) String> contentTypes;

    @Size(max = 20)
    private List<@Size(max = 64) String> topicCodes;

    @Size(max = 5)
    private List<@Size(max = 24) String> validityStatuses;

    private Long docId;

    /** Explicit opt-in to historical material in the party portal. */
    private Boolean historical;
}



