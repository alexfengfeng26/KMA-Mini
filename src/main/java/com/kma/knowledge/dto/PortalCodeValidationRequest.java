package com.kma.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PortalCodeValidationRequest {
    @NotNull
    private Map<String, String> files;
    private JsonNode manifest;
}
