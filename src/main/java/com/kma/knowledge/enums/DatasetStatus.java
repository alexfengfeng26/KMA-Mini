package com.kma.knowledge.enums;

import lombok.Getter;

/**
 * 数据集状态
 *
 * @author party
 * @date 2026/06/30
 */
@Getter
public enum DatasetStatus {

    ACTIVE("active", "启用"),
    DISABLED("disabled", "禁用");

    private final String code;
    private final String desc;

    DatasetStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}



