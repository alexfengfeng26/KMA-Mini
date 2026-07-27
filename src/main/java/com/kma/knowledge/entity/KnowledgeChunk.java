package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kma.knowledge.config.PgvectorTypeHandler;
import com.kma.knowledge.config.TsvectorTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 知识分块：向量 + 全文检索混合召回
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long chunkId;

    private Long docId;

    private Long spaceId;

    private Integer chunkIndex;

    private String content;

    private Integer charOffset;

    private Integer tokenCount;

    private String sourceTag;

    /**
     * 向量：使用 pgvector 存储
     */
    @TableField(typeHandler = PgvectorTypeHandler.class)
    private float[] embedding;

    /** Target embedding written to the versioned embedding table. */
    @TableField(exist = false)
    private float[] targetEmbedding;

    /**
     * 全文检索向量：数据库自动生成，Java 侧只读
     */
    @TableField(typeHandler = TsvectorTypeHandler.class)
    private String fullTextVector;

    /** Application-tokenized text used by the portable Chinese lexical index. */
    private String searchText;

    @TableField(typeHandler = TsvectorTypeHandler.class)
    private String searchVector;

    private String embeddingModel;

    /**
     * JSONB：分块级元数据
     */
    private String meta;

    private LocalDateTime createTime;

    /**
     * 关联文档标题（非数据库字段）
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String docTitle;

    /**
     * 关联文档外部引用（非数据库字段）
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String externalRef;

    @TableField(exist = false)
    private String documentNumber;

    @TableField(exist = false)
    private String issuingAuthority;

    @TableField(exist = false)
    private LocalDate publishDate;

    @TableField(exist = false)
    private String validityStatus;

    /** SQL-calculated similarity/rank score, not persisted. */
    @TableField(exist = false)
    private Double retrievalScore;
}



