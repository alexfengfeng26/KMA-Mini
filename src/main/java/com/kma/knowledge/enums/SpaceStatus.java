package com.kma.knowledge.enums;

import lombok.Getter;

/**
 * 知识空间状态
 *
 * @author party
 * @date 2026/06/30
 */
@Getter
public enum SpaceStatus {

    ACTIVE("active", "启用"),
    DISABLED("disabled", "禁用");

    private final String code;
    private final String desc;

    SpaceStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}



