package com.kma.knowledge.api;

import com.kma.knowledge.api.dto.KnowledgeFeedRequest;

/**
 * 知识库摄入契约。
 * <p>
 * 供其它业务模块投递文本到知识库，避免跨模块直接依赖 {@code KnowledgeIngestionService}。
 */
public interface KnowledgeIngestionApi {

    /**
     * 投递纯文本到知识库。
     *
     * @param request 投喂请求
     */
    void ingestText(KnowledgeFeedRequest request);
}



