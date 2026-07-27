package com.kma.common.security.dto;

import jakarta.validation.constraints.NotNull;

public record OrganizationMoveRequest(@NotNull Long parentId) {}
