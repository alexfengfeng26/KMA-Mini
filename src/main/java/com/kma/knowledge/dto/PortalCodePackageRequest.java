package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PortalCodePackageRequest {
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$")
    private String packageKey;

    @NotBlank
    @Size(max = 128)
    private String displayName;

    @Size(max = 500)
    private String description;
}
