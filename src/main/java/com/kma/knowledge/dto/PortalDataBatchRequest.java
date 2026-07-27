package com.kma.knowledge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PortalDataBatchRequest {
    @NotEmpty
    @Size(max = 50)
    @Valid
    private List<Query> queries;

    @Data
    public static class Query {
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$")
        private String id;

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "documents|categories|topics|favorites|history|announcements|static")
        private String source;

        @Size(max = 20)
        private Map<String, String> filters;
    }
}
