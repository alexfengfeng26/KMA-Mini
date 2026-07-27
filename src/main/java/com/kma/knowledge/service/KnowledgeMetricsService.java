package com.kma.knowledge.service;

import java.util.Map;

/**
 * 知识库指标服务接口
 *
 * @author party
 * @date 2026/07/02
 */
public interface KnowledgeMetricsService {

    /**
     * 获取 RAG 运行大盘聚合指标
     */
    Map<String, Object> getDashboardMetrics(String spaceCode);
}



