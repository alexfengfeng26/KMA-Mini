package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据集更新请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "DatasetUpdateRequest", description = "DatasetUpdateRequest 数据模型")
public class DatasetUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "数据集 ID 不能为空")
    private Long datasetId;

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 128, message = "数据集名称长度不能超过 128")
    private String name;

    @Size(max = 512, message = "描述长度不能超过 512")
    private String description;

    private String chunkStrategy;

    private String parseConfig;

    /** 兼容旧数据：仅允许尚未绑定的数据集首次绑定，绑定后不可修改。 */
    @Size(max = 64, message = "Embedding Profile 编码长度不能超过 64")
    private String embeddingProfileCode;

    private Boolean rerankEnabled;

    @Size(max = 64, message = "重排模型长度不能超过 64")
    private String rerankModel;

    private String presetQuestions;
}



