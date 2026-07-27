package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 检索调试结果
 *
 * @author party
 * @date 2026/06/30
 */
@Data
public class RetrieveDebugResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;

    private String spaceCode;

    private List<ChunkHitVO> vectorHits;

    private List<ChunkHitVO> fullTextHits;

    private List<ChunkHitVO> rerankedHits;

    private List<ChunkHitVO> finalHits;

    private Map<String, Long> latency;
}



