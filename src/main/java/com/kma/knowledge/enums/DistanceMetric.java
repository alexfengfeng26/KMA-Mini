package com.kma.knowledge.enums;

import lombok.Getter;

/**
 * 向量距离度量方式
 *
 * @author party
 * @date 2026/06/30
 */
@Getter
public enum DistanceMetric {

    COSINE("cosine", "余弦相似度"),
    EUCLIDEAN("euclidean", "欧氏距离"),
    DOT("dot", "点积");

    private final String code;
    private final String desc;

    DistanceMetric(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}



