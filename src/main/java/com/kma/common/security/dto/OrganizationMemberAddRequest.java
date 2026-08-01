package com.kma.common.security.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrganizationMemberAddRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> userIds,
    boolean primary
) {}
