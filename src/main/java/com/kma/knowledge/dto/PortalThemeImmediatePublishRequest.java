package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Request for the administrator-only direct Theme V4 publication operation. */
@Data
public class PortalThemeImmediatePublishRequest {
    @NotNull
    @Positive
    private Long themeVersionId;

    /** When true the checked-out local package is snapshotted before it is applied and published. */
    private boolean syncLocalSource;
}
