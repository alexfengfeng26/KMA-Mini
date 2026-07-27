package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kma.knowledge.config.PgvectorTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk_embedding")
public class KnowledgeChunkEmbedding {
    @TableId(type = IdType.AUTO)
    private Long embeddingId;
    private Long chunkId;
    private Long spaceId;
    private String profileCode;
    private String modelName;
    private Integer dimension;
    @TableField(typeHandler = PgvectorTypeHandler.class)
    private float[] embedding;
    private Boolean active;
    private LocalDateTime createTime;
}
