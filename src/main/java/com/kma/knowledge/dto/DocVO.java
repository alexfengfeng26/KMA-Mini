package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识文档分页视图对象
 *
 * @author party
 * @date 2026/07/01
 */
@Data
@Schema(name = "DocVO", description = "DocVO 数据模型")
public class DocVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long docId;

    private Long spaceId;

    private String spaceCode;

    private String title;

    private String sourceTag;

    private String externalRef;

    private Long sourceVersion;

    private Boolean isActive;

    private Long supersedesDocId;

    private LocalDateTime activatedAt;

    private String mimeType;

    private String contentHash;

    private Long storageObjectId;

    private Long storageSizeBytes;

    private String parseStatus;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



