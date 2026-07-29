package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kma.knowledge.config.JsonbStringTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识文档
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@TableName("knowledge_doc")
public class KnowledgeDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long docId;

    private Long spaceId;

    private String title;

    /**
     * 业务方传入的不透明来源标签
     */
    private String sourceTag;

    /**
     * 业务方用于回溯的外部引用
     */
    private String externalRef;

    private Long sourceVersion;

    /** Whether this version is visible to retrieval. */
    private Boolean isActive;

    /** Previous active version retained until this version is fully indexed. */
    private Long supersedesDocId;

    private LocalDateTime activatedAt;

    /** Whether this document is governed by the draft/review/publish workflow. */
    private Boolean publicationManaged;

    private String contentType;

    private String documentNumber;

    private String issuingAuthority;

    private LocalDate publishDate;

    private LocalDate effectiveDate;

    private LocalDate expiryDate;

    private LocalDateTime scheduledOnlineAt;

    private LocalDateTime scheduledOfflineAt;

    private String scheduleNote;

    private Long createdBy;

    private String validityStatus;

    private String workflowStatus;

    private String reviewDecision;

    private String reviewNote;

    private Boolean online;

    private String summary;

    /** JSON array of normalized keyword strings. */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String keywords;

    private Long reviewerId;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    private LocalDateTime publishedAt;

    private String mimeType;

    private String storagePath;

    private Long storageObjectId;

    private Long storageSizeBytes;

    private String contentHash;

    private String parseStatus;

    private Integer chunkCount;

    private String errorMessage;

    /**
     * JSONB：业务自定义元数据
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String meta;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



