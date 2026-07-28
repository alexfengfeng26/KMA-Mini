package com.kma.knowledge.dto;

import java.util.List;
import java.util.Map;

public record PortalThemeDesignProposalResponse(
    String model,
    String summary,
    List<String> warnings,
    Map<String, String> files,
    List<String> changedFiles,
    int promptTokens,
    int completionTokens
) {}
