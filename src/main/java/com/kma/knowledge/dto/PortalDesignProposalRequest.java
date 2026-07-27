package com.kma.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PortalDesignProposalRequest {
    @NotNull
    private Long versionId;

    @NotNull
    @Min(0)
    private Integer expectedLockVersion;

    @NotNull
    private JsonNode config;

    @NotBlank
    @Pattern(regexp = "page|node")
    private String scope;

    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$")
    private String pageSlug;

    @Size(max = 64)
    private String nodeId;

    @NotBlank
    @Size(max = 2000)
    private String instruction;
}
