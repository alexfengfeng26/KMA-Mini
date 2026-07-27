package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PortalVersionActionRequest {
    @NotNull
    @Positive
    private Long versionId;

    @Size(max = 1000)
    private String note;
}
