package com.kma.knowledge.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class PortalThemeDesignProposalRequest {
    @NotNull
    @Min(0)
    private Integer expectedLockVersion;

    @NotEmpty
    @Size(max = 100)
    private Map<String, String> files;

    @NotBlank
    @Size(max = 2000)
    private String instruction;
}
