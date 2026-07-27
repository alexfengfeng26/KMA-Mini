package com.kma.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PortalContentQuery {
    @Size(max = 255) private String keyword;
    @Size(max = 32) private String contentType;
    @Size(max = 64) private String topicCode;
    @Size(max = 255) private String issuingAuthority;
    @Size(max = 24) private String validityStatus;
    @Size(max = 64) private String spaceCode;
    private LocalDate publishDateFrom;
    private LocalDate publishDateTo;
    private Boolean includeHistorical = false;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 20;
}
