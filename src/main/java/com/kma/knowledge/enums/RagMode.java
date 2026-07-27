package com.kma.knowledge.enums;

import lombok.Getter;

/**
 * RAG 调用模式
 *
 * @author party
 * @date 2026/06/30
 */
@Getter
public enum RagMode {

    RETRIEVE("retrieve", "纯检索"),
    QA("qa", "问答");

    private final String code;
    private final String desc;

    RagMode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}



