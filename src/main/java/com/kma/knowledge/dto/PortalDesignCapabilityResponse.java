package com.kma.knowledge.dto;

public record PortalDesignCapabilityResponse(
    boolean available,
    String provider,
    String model,
    String reason
) {}
