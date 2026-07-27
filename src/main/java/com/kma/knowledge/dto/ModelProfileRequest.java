package com.kma.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModelProfileRequest {
    private Long profileId;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,64}")
    private String profileCode;
    @NotBlank @Size(max = 128)
    private String name;
    @NotBlank @Pattern(regexp = "llm|embedding|rerank|ocr")
    private String capability;
    @NotBlank @Size(max = 32)
    private String provider;
    @NotBlank @Size(max = 128)
    private String modelName;
    @Size(max = 512)
    private String baseUrl;
    private Integer dimension;
    @Min(1) @Max(600)
    private Integer timeoutSeconds = 60;
    @Size(max = 128)
    private String secretAlias;
    private String fallbackProfileCodes;
    private Boolean enabled = true;
    private Boolean defaultProfile = false;
}
