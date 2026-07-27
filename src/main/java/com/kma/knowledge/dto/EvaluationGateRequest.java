package com.kma.knowledge.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class EvaluationGateRequest {
    @DecimalMin("0.0") @DecimalMax("1.0") private double minRecallAtK = 0.80;
    @DecimalMin("0.0") @DecimalMax("1.0") private double minMrr = 0.60;
    @DecimalMin("0.0") @DecimalMax("1.0") private double minCitationPrecision = 0.80;
    @DecimalMin("0.0") @DecimalMax("1.0") private double minRefusalAccuracy = 0.90;
    @DecimalMin("0.0") @DecimalMax("1.0") private double minAnswerCorrectness = 0.70;
    @Min(1) private int minCaseCount = 1;
    private boolean enabled = true;
}
