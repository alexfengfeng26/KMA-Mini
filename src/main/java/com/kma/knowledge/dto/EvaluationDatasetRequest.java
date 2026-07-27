package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EvaluationDatasetRequest {
    @NotBlank @Size(max = 128)
    private String name;
    @NotBlank @Size(max = 64)
    private String spaceCode;
    @Size(max = 512)
    private String description;
}
