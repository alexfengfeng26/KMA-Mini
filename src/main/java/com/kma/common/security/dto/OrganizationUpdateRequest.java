package com.kma.common.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationUpdateRequest(
    @NotBlank @Size(max = 128) String name,
    @Pattern(regexp = "active|disabled") String status,
    Integer sortOrder
) {}
