package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据集视图对象
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "DatasetVO", description = "DatasetVO 数据模型")
public class DatasetVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long datasetId;

    private String name;

    private String description;

    private String chunkStrategy;

    private String parseConfig;

    private String embeddingProfileCode;

    private Boolean rerankEnabled;

    private String rerankModel;

    private String presetQuestions;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



