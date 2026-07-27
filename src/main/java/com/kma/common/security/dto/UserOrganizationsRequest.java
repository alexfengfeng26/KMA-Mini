package com.kma.common.security.dto;

import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.Set;

public record UserOrganizationsRequest(
    @Size(max = 100) Set<Long> organizationIds,
    Long primaryOrganizationId
) {
    public UserOrganizationsRequest {
        organizationIds = organizationIds == null ? new LinkedHashSet<>() : organizationIds;
    }
}
