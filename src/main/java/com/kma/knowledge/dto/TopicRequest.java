package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TopicRequest {
    @NotBlank @Size(max = 64) private String topicCode;
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 1000) private String description;
    @Size(max = 24) private String coverColor;
    private Integer sortOrder = 0;
    private Boolean enabled = true;
    private Boolean featured = false;
    private Long parentTopicId;
    @Pattern(regexp = "category|topic|channel") private String topicType = "topic";
    @Size(max = 64) private String icon;
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,95}$") private String slug;
    @Pattern(regexp = "list|cards|timeline|faq") private String displayMode = "list";
}
