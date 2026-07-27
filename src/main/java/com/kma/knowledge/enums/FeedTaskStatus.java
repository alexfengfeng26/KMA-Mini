package com.kma.knowledge.enums;

import lombok.Getter;

/**
 * 知识库自动投喂任务状态
 *
 * @author party
 * @date 2026/07/02
 */
@Getter
public enum FeedTaskStatus {

    PENDING("pending", "待处理"),
    PROCESSING("processing", "处理中"),
    SUCCESS("success", "成功"),
    DEAD("dead", "死信");

    private final String code;
    private final String desc;

    FeedTaskStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}



