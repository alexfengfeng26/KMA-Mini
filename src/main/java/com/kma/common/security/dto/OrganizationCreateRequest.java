package com.kma.common.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationCreateRequest(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{1,63}") String orgCode,
    @NotBlank @Size(max = 128) String name,
    @NotNull Long parentId,
    Integer sortOrder
) {}
