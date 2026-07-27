package com.kma.knowledge.service;

import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.RetrieveDebugResult;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.dto.RetrieveResult;

import java.util.List;

/**
 * 知识库检索服务接口
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeRetrieveService {

    /**
     * 执行检索
     */
    RetrieveResult retrieve(RetrieveRequest request);

    /**
     * 检索调试
     */
    RetrieveDebugResult debug(RetrieveRequest request);

    /**
     * 直接检索分块（供 QA 服务复用）
     */
    List<ChunkHitVO> retrieveChunks(RetrieveRequest request);
}



