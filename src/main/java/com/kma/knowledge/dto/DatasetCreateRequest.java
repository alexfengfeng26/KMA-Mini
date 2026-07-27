package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据集创建请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "DatasetCreateRequest", description = "DatasetCreateRequest 数据模型")
public class DatasetCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 128, message = "数据集名称长度不能超过 128")
    private String name;

    @Size(max = 512, message = "描述长度不能超过 512")
    private String description;

    /**
     * JSON 字符串：分块策略
     */
    private String chunkStrategy;

    /**
     * JSON 字符串：解析配置
     */
    private String parseConfig;

    @Size(max = 64, message = "Embedding Profile 编码长度不能超过 64")
    private String embeddingProfileCode;

    private Boolean rerankEnabled;

    @Size(max = 64, message = "重排模型长度不能超过 64")
    private String rerankModel;

    /**
     * JSON 字符串：预设问题
     */
    private String presetQuestions;
}



