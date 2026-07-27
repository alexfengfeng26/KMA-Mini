package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识空间更新请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "SpaceUpdateRequest", description = "SpaceUpdateRequest 数据模型")
public class SpaceUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "空间 ID 不能为空")
    private Long spaceId;

    private Long datasetId;

    @NotBlank(message = "空间名称不能为空")
    @Size(max = 128, message = "空间名称长度不能超过 128")
    private String name;

    @Size(max = 512, message = "描述长度不能超过 512")
    private String description;

    @NotBlank(message = "Embedding 模型不能为空")
    @Size(max = 64, message = "Embedding 模型长度不能超过 64")
    private String embeddingModel;

    @Size(max = 16, message = "距离度量长度不能超过 16")
    private String distanceMetric;

    private String chunkStrategy;

    private Integer defaultTopK;

    private BigDecimal scoreThreshold;
}



