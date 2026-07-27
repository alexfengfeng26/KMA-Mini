package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 检索命中的分块视图
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@Schema(name = "ChunkHitVO", description = "ChunkHitVO 数据模型")
public class ChunkHitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long chunkId;

    private Long docId;

    private Long spaceId;

    private String spaceCode;

    private String docTitle;

    private String sourceTag;

    private String externalRef;

    private String content;

    /**
     * 综合得分（rerank 后）或向量相似度
     */
    private Double score;

    /** Raw cosine similarity returned by pgvector. */
    private Double vectorScore;

    /** Raw PostgreSQL full-text rank. */
    private Double fullTextScore;

    /** Normalized reciprocal-rank-fusion score. */
    private Double rrfScore;

    /** Cross-encoder or fallback reranker score. */
    private Double rerankScore;

    /**
     * 命中环节：vector / fulltext / rerank
     */
    private String sourceStage;

    private Integer chunkIndex;

    private String meta;

    private String documentNumber;

    private String issuingAuthority;

    private LocalDate publishDate;

    private String validityStatus;

    private Integer pageNumber;

    private String section;
}



