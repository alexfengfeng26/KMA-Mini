package com.kma.knowledge.service;

import java.util.Set;

/** Published site scope. It is always intersected with the current user's space ACL. */
public record PortalContentScope(
    boolean allSpaces,
    Set<String> spaceCodes,
    Set<String> topicCodes,
    Set<String> contentTypes,
    Set<String> validityStatuses
) {
    public static PortalContentScope unrestricted() {
        return new PortalContentScope(true, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public PortalContentScope {
        spaceCodes = spaceCodes == null ? Set.of() : Set.copyOf(spaceCodes);
        topicCodes = topicCodes == null ? Set.of() : Set.copyOf(topicCodes);
        contentTypes = contentTypes == null ? Set.of() : Set.copyOf(contentTypes);
        validityStatuses = validityStatuses == null ? Set.of() : Set.copyOf(validityStatuses);
    }
}
