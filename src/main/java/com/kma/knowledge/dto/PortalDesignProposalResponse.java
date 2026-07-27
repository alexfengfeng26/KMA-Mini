package com.kma.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record PortalDesignProposalResponse(
    String scope,
    String pageSlug,
    String nodeId,
    String model,
    String summary,
    List<String> warnings,
    JsonNode target,
    int promptTokens,
    int completionTokens
) {}
