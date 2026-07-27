package com.kma.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class PortalCodeFilesRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$")
    private String version;

    private JsonNode manifest;

    @Size(min = 1, max = 50)
    private Map<String, String> files;
}
