package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PortalSiteUpdateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Pattern(regexp = "active|disabled")
    private String status;

    private Boolean defaultSite = false;
}
