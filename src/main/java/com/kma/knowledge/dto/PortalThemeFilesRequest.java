package com.kma.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PortalThemeFilesRequest {
    @NotNull
    private Map<String, String> files;
    private JsonNode manifest;
    @NotNull
    @Min(0)
    private Integer expectedLockVersion;
}
