package com.kma.common.security.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserBatchStatusRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> userIds,
    @Pattern(regexp = "active|disabled") String status
) {}
