package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QaFeedbackRequest {
    @NotBlank @Pattern(regexp = "helpful|unhelpful") private String rating;
    @Size(max = 64) private String reason;
    @Size(max = 1000) private String comment;
    @Size(max = 64) private String spaceCode;
    private Long sessionId;
    @Size(max = 2000) private String question;
    @Size(max = 4000) private String answerExcerpt;
    @Size(max = 20) private List<@Size(max = 256) String> citationRefs = List.of();
}
