package com.kma.knowledge.service;

import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.QAResult;

/**
 * 知识库问答服务接口
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeQAService {

    /**
     * 端到端问答
     */
    QAResult answer(QARequest request);
}



