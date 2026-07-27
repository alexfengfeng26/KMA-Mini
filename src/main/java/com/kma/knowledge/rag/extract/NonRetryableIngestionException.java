package com.kma.knowledge.rag.extract;

/** 无需重试的文档/向量输入错误，必须原类型穿透 Pipeline 供 Worker 分类。 */
public class NonRetryableIngestionException extends RuntimeException {
    public NonRetryableIngestionException(String message) {
        super(message);
    }

    public NonRetryableIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
