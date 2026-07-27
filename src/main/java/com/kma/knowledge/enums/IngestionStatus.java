package com.kma.knowledge.enums;

import lombok.Getter;

/**
 * 文档摄入状态机
 *
 * @author party
 * @date 2026/06/30
 */
@Getter
public enum IngestionStatus {

    PENDING("pending", "待处理"),
    PARSING("parsing", "解析中"),
    CHUNKING("chunking", "分块中"),
    EMBEDDING("embedding", "向量化中"),
    NEEDS_OCR("needs_ocr", "需要 OCR"),
    SUPERSEDED("superseded", "已被更高版本取代"),
    COMPLETED("completed", "已完成"),
    FAILED("failed", "失败");

    private final String code;
    private final String desc;

    IngestionStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}



