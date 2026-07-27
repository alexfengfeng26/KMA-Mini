package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识空间视图对象
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "SpaceVO", description = "SpaceVO 数据模型")
public class SpaceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long spaceId;

    private Long datasetId;

    private String spaceCode;

    private String name;

    private String description;

    private String embeddingProvider;

    private String embeddingModel;

    private Integer embeddingDim;

    private String distanceMetric;

    private String chunkStrategy;

    private Integer defaultTopK;

    private BigDecimal scoreThreshold;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



