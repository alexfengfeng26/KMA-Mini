package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 检索结果
 *
 * @author party
 * @date 2026/06/30
 */
@Data
public class RetrieveResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;

    private String spaceCode;

    private Integer topK;

    private List<ChunkHitVO> hits;
}



