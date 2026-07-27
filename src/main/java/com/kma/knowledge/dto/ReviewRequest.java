package com.kma.knowledge.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewRequest {
    @Size(max = 1000)
    private String note;
}
