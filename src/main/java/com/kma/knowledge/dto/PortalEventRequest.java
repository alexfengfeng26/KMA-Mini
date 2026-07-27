package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class PortalEventRequest {
    @NotBlank
    @Pattern(regexp = "page_view|search|search_empty|article_click|ai_ask|feedback")
    private String eventType;

    @Size(max = 64)
    private String pageSlug;

    @Size(max = 500)
    private String queryText;

    @Size(max = 128)
    private String targetId;

    private Map<String, Object> metadata = Map.of();
}
