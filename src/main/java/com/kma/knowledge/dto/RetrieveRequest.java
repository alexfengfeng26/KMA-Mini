package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 检索请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "RetrieveRequest", description = "RetrieveRequest 数据模型")
public class RetrieveRequest implements Serializable {

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
    @Size(max = 20, message = "来源标签不能超过 20 个")
    private List<@Size(max = 64) String> sourceTags;

    /**
     * 返回数量
     */
    @Min(1) @Max(100)
    private Integer topK;

    /**
     * 相似度阈值
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private BigDecimal scoreThreshold;

    /** Restrict retrieval to publication-managed, published and online content. */
    private Boolean portalOnly;

    @Size(max = 5)
    private List<@Size(max = 32) String> contentTypes;

    @Size(max = 20)
    private List<@Size(max = 64) String> topicCodes;

    @Size(max = 5)
    private List<@Size(max = 24) String> validityStatuses;

    private Long docId;
}



