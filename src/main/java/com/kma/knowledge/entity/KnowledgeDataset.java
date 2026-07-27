package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kma.knowledge.config.JsonbStringTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据集：把一类知识的解析、分块、重排策略打包
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@TableName(value = "knowledge_dataset", autoResultMap = true)
public class KnowledgeDataset implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long datasetId;

    private String name;

    private String description;

    /**
     * JSONB：分块策略配置
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String chunkStrategy;

    /**
     * JSONB：文档解析配置
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String parseConfig;

    /** Immutable embedding profile binding. Legacy datasets may bind it once. */
    private String embeddingProfileCode;

    private Boolean rerankEnabled;

    private String rerankModel;

    /**
     * JSONB：预设问题列表
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String presetQuestions;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



