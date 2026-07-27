package com.kma.knowledge.enums;

import lombok.Getter;

@Getter
public enum IngestionJobStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    SUCCEEDED("succeeded"),
    DEAD("dead");

    private final String code;

    IngestionJobStatus(String code) {
        this.code = code;
    }
}
