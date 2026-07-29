package com.kma.knowledge.dto;

import lombok.Data;

/** Safe-to-return diagnostic for a model profile connectivity probe. */
@Data
public class ModelProfileProbeResult {
    private String profileCode;
    private String capability;
    private String modelName;
    private boolean success;
    private boolean nonStreamingSupported;
    private boolean streamingSupported;
    private long durationMillis;
    /** A categorized message only; it must never contain a secret or response body. */
    private String message;
}
