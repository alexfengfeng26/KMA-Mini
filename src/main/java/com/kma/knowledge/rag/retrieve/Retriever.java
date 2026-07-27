package com.kma.knowledge.rag.retrieve;

import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.RetrieveRequest;

import java.util.List;

/**
 * 检索器抽象
 *
 * @author party
 * @date 2026/06/30
 */
public interface Retriever {

    /**
     * 执行检索
     */
    List<ChunkHitVO> retrieve(RetrieveRequest request);
}



