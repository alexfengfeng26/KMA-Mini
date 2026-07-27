package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文件摄入请求参数
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "DocIngestFileRequest", description = "DocIngestFileRequest 数据模型")
public class DocIngestFileRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "空间编码不能为空")
    @Size(max = 64, message = "空间编码长度不能超过 64")
    private String spaceCode;

    @Size(max = 64, message = "来源标签长度不能超过 64")
    private String sourceTag;

    @Size(max = 256, message = "外部引用长度不能超过 256")
    private String externalRef;

    /** 来源版本；同一 externalRef 只接受更高版本。 */
    private Long sourceVersion = 1L;

    /**
     * JSON 字符串：业务自定义元数据
     */
    private String meta;
}



