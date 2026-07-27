package com.kma.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PortalConfigDraftRequest {
    private JsonNode config;

    @Min(0)
    private Integer expectedLockVersion;

    @Size(max = 1000)
    private String changeNote;
}
