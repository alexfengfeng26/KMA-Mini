package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FavoriteRequest {
    @NotBlank @Pattern(regexp = "content|qa") private String favoriteType;
    private Long docId;
    private Long sessionId;
    @Size(max = 255) private String title;
}
