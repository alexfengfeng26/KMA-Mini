package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PortalSiteCreateRequest {
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$")
    private String siteKey;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Pattern(regexp = "party|internal-policy|product-help")
    private String scenario;

    private Boolean defaultSite = false;
}
