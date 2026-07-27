package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kma.knowledge.config.JsonbStringTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识空间：多来源隔离的核心单位
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@TableName(value = "knowledge_space", autoResultMap = true)
public class KnowledgeSpace implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long spaceId;

    private Long datasetId;

    private String spaceCode;

    private String name;

    private String description;

    private String embeddingProvider;

    private String embeddingModel;

    private Integer embeddingDim;

    private String distanceMetric;

    /**
     * JSONB：空间级分块策略（可覆盖数据集配置）
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String chunkStrategy;

    private Integer defaultTopK;

    private BigDecimal scoreThreshold;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



