package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EvaluationCaseRequest {
    @NotBlank @Size(max = 2000)
    private String question;
    @Size(max = 20)
    private List<@Size(max = 256) String> expectedExternalRefs = List.of();
    @Size(max = 10000)
    private String expectedAnswer;
    private boolean shouldRefuse;
}
