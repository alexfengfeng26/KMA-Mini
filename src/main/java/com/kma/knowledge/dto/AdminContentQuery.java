package com.kma.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminContentQuery {
    @Size(max = 255) private String keyword;
    @Size(max = 32) private String contentType;
    @Size(max = 24) private String workflowStatus;
    @Size(max = 24) private String reviewDecision;
    @Size(max = 64) private String spaceCode;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 20;
}
