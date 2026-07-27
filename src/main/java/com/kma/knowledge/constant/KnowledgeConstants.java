package com.kma.knowledge.constant;

/**
 * 知识库模块通用常量
 *
 * @author party
 * @date 2026/06/30
 */
public final class KnowledgeConstants {

    private KnowledgeConstants() {
    }

    /**
     * 默认分块策略类型
     */
    public static final String DEFAULT_CHUNK_STRATEGY = "fixed_size";

    /**
     * 段落分块策略类型
     */
    public static final String PARAGRAPH_CHUNK_STRATEGY = "paragraph";

    /**
     * 默认分页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 默认 topK
     */
    public static final int DEFAULT_TOP_K = 5;

    /**
     * 默认相似度阈值
     */
    public static final double DEFAULT_SCORE_THRESHOLD = 0.35;

    /**
     * RAG 模式：纯检索
     */
    public static final String RAG_MODE_RETRIEVE = "retrieve";

    /**
     * RAG 模式：端到端问答
     */
    public static final String RAG_MODE_QA = "qa";

    /**
     * 知识空间状态：正常
     */
    public static final String STATUS_ACTIVE = "active";

    /**
     * 知识空间状态：停用
     */
    public static final String STATUS_INACTIVE = "inactive";

    /**
     * 摄入状态：待处理
     */
    public static final String INGEST_STATUS_PENDING = "pending";

    /**
     * 摄入状态：解析中
     */
    public static final String INGEST_STATUS_PARSING = "parsing";

    /**
     * 摄入状态：分块中
     */
    public static final String INGEST_STATUS_CHUNKING = "chunking";

    /**
     * 摄入状态：向量化中
     */
    public static final String INGEST_STATUS_EMBEDDING = "embedding";

    /**
     * 摄入状态：完成
     */
    public static final String INGEST_STATUS_COMPLETED = "completed";

    /**
     * 摄入状态：失败
     */
    public static final String INGEST_STATUS_FAILED = "failed";

    /**
     * 默认 Embedding 提供商
     */
    public static final String DEFAULT_EMBEDDING_PROVIDER = "zhipu";

    /**
     * 默认 LLM 提供商
     */
    public static final String DEFAULT_LLM_PROVIDER = "deepseek";

    /**
     * 默认 Rerank 提供商
     */
    public static final String DEFAULT_RERANK_PROVIDER = "bge-reranker-base";

    /**
     * 本地 BGE-M3 提供商（预留）
     */
    public static final String LOCAL_BGE_M3_PROVIDER = "local-bge-m3";
}



