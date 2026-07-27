package com.kma.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 独立 AI 知识库配置属性
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    /**
     * 是否启用知识库模块（默认关闭）
     * <p>
     * 关闭时不会创建 PostgreSQL 数据源。
     */
    private boolean enabled = false;

    /**
     * PostgreSQL 数据源配置
     */
    private DataSourceProperties datasource = new DataSourceProperties();

    /**
     * 文件存储路径（默认相对项目根目录）
     */
    private StorageProperties storage = new StorageProperties();

    private DocumentProperties document = new DocumentProperties();

    private OcrProperties ocr = new OcrProperties();

    /**
     * 嵌入式模型配置
     */
    private EmbeddingProperties embedding = new EmbeddingProperties();

    /**
     * 大语言模型配置
     */
    private LlmProperties llm = new LlmProperties();

    /**
     * 重排序模型配置
     */
    private RerankProperties rerank = new RerankProperties();

    private RagProperties rag = new RagProperties();

    /**
     * 自动投喂重试配置
     */
    private FeedProperties feed = new FeedProperties();

    /** Durable document ingestion worker configuration. */
    private IngestionProperties ingestion = new IngestionProperties();

    /**
     * 数据治理配置
     */
    private DataGovernanceProperties governance = new DataGovernanceProperties();

    @Data
    public static class DataSourceProperties {
        private String driverClassName = "org.postgresql.Driver";
        private String jdbcUrl = "jdbc:postgresql://localhost:5432/kma_mini";
        private String username = "kma";
        private String password = "kma";
        private int maximumPoolSize = 20;
        private int minimumIdle = 5;
    }

    @Data
    public static class StorageProperties {
        private String type = "local";
        private String path = "upload/knowledge";
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "kma-documents";
        private String region;
        private boolean lifecycleEnabled = true;
        private int reconciliationBatchSize = 1000;
        private int cleanupBatchSize = 100;
        private int orphanGraceHours = 24;
    }

    @Data
    public static class DocumentProperties {
        private int maxPages = 1000;
        private int maxExtractedChars = 5_000_000;
        private int maxArchiveEntries = 10_000;
        private long maxUncompressedBytes = 536_870_912L;
        private int maxCompressionRatio = 100;
    }

    @Data
    public static class OcrProperties {
        private boolean enabled;
        private String baseUrl = "http://localhost:8866";
        private String endpoint = "/api/v1/ocr";
        private String apiKey;
        private int connectTimeoutSeconds = 5;
        private int readTimeoutSeconds = 120;
    }

    @Data
    public static class EmbeddingProperties {
        private String defaultProvider = "zhipu";
        private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
        private String model = "embedding-2";
        private int dimension = 1024;
        private int maxBatchSize = 16;
        private int maxRetry = 3;
        private String apiKey;
        /**
         * 逗号分隔的备用提供商列表，如：local-bge-m3,openai
         */
        private String fallbackProviders;
        /**
         * 本地 Embedding 服务配置（xinference / ollama）
         */
        private LocalEmbeddingProperties local = new LocalEmbeddingProperties();
    }

    @Data
    public static class LocalEmbeddingProperties {
        /**
         * 本地服务基础地址，默认指向 xinference 的 OpenAI 兼容端点
         */
        private String baseUrl = "http://localhost:9997/v1";
        /**
         * 本地模型名，如 bge-m3
         */
        private String model = "bge-m3";
        private String apiKey;
        private int maxRetry = 3;
    }

    @Data
    public static class LlmProperties {
        private String defaultProvider = "deepseek";
        private String baseUrl = "https://api.deepseek.com/v1";
        private String model = "deepseek-chat";
        private int maxRetry = 3;
        private String apiKey;
        /**
         * 逗号分隔的备用 LLM 提供商列表，如：zhipu,ollama
         */
        private String fallbackProviders;
        /**
         * 本地 LLM 服务配置（ollama / xinference 等 OpenAI 兼容端点）
         */
        private LocalLlmProperties local = new LocalLlmProperties();
    }

    @Data
    public static class LocalLlmProperties {
        /**
         * 本地服务基础地址，默认指向 ollama 的 OpenAI 兼容端点
         */
        private String baseUrl = "http://localhost:11434/v1";
        /**
         * 本地模型名，如 qwen2.5, llama3, deepseek-r1:7b
         */
        private String model = "qwen2.5";
        private String apiKey = "ollama";
        private int maxRetry = 3;
    }

    @Data
    public static class RerankProperties {
        private String defaultProvider = "bge-reranker-base";
        private String baseUrl;
        private String apiKey;
    }

    @Data
    public static class RagProperties {
        /** @deprecated use maxContextTokens; kept only for configuration compatibility. */
        @Deprecated
        private int maxContextChars = 12000;
        private int maxContextTokens = 6000;
        private int maxHistoryTokens = 1000;
        private int reservedOutputTokens = 1000;
        private int maxHistoryMessages = 6;
        private String noEvidenceAnswer = "未在当前知识空间中找到足够依据，暂时无法可靠回答。";
    }

    @Data
    public static class FeedProperties {
        /**
         * 是否启用自动投喂重试调度
         */
        private boolean enabled = true;

        /**
         * 最大重试次数
         */
        private int maxRetry = 3;

        /**
         * 初始重试间隔（秒）
         */
        private int initialIntervalSeconds = 60;

        /**
         * 最大重试间隔（秒）
         */
        private int maxIntervalSeconds = 3600;

        /**
         * 每轮调度处理任务数
         */
        private int batchSize = 10;

        /**
         * 调度固定间隔（毫秒）
         */
        private long fixedDelay = 30000L;
    }

    @Data
    public static class IngestionProperties {
        private boolean workerEnabled = true;
        private int maxRetry = 3;
        private int batchSize = 4;
        private int leaseSeconds = 300;
        private int initialRetrySeconds = 30;
        private int maxRetrySeconds = 1800;
        private long fixedDelay = 2000L;
    }

    @Data
    public static class DataGovernanceProperties {
        /**
         * 是否启用数据治理定时任务
         */
        private boolean enabled = true;

        /**
         * 清理 cron 表达式，默认每天 2 点
         */
        private String cron = "0 0 2 * * ?";

        /**
         * 失败文档保留天数
         */
        private int failedDocRetentionDays = 7;
    }
}



