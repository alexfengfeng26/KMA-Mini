package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识空间创建请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "SpaceCreateRequest", description = "SpaceCreateRequest 数据模型")
public class SpaceCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long datasetId;

    @NotBlank(message = "空间编码不能为空")
    @Size(max = 64, message = "空间编码长度不能超过 64")
    private String spaceCode;

    @NotBlank(message = "空间名称不能为空")
    @Size(max = 128, message = "空间名称长度不能超过 128")
    private String name;

    @Size(max = 512, message = "描述长度不能超过 512")
    private String description;

    @NotBlank(message = "Embedding 提供商不能为空")
    @Size(max = 32, message = "Embedding 提供商长度不能超过 32")
    private String embeddingProvider;

    @NotBlank(message = "Embedding 模型不能为空")
    @Size(max = 64, message = "Embedding 模型长度不能超过 64")
    private String embeddingModel;

    @NotNull(message = "向量维度不能为空")
    private Integer embeddingDim;

    @Size(max = 16, message = "距离度量长度不能超过 16")
    private String distanceMetric;

    /**
     * JSON 字符串：分块策略
     */
    private String chunkStrategy;

    private Integer defaultTopK;

    private BigDecimal scoreThreshold;
}



