package com.kma.knowledge.service;

/**
 * 知识库数据治理服务
 *
 * @author party
 * @date 2026/07/02
 */
public interface KnowledgeDataGovernanceService {

    /**
     * 执行数据治理清理：孤儿分块、过期失败文档
     */
    void cleanup();

    /**
     * 重新索引指定空间下的全部文档
     */
    void reindexSpace(String spaceCode);
}



