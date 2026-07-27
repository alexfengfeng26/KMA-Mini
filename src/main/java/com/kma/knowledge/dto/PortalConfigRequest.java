package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PortalConfigRequest {
    @NotBlank @Size(max = 255) private String unitName;
    @Size(max = 1000) private String helpText;
    @Size(max = 64) private String currentTopicCode;
}
