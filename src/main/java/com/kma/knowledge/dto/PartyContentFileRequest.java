package com.kma.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** Multipart form metadata for a publication-managed source file. */
@Data
public class PartyContentFileRequest {
    @NotBlank @Size(max = 255) private String title;
    @NotBlank @Size(max = 64) private String spaceCode;
    @Size(max = 64) private String sourceTag;
    @Size(max = 256) private String externalRef;
    private Long sourceVersion = 1L;
    @NotBlank @Pattern(regexp = "party_constitution|policy|learning_material|grassroots_case|organization_system")
    private String contentType;
    @Size(max = 128) private String documentNumber;
    @Size(max = 255) private String issuingAuthority;
    @NotNull private LocalDate publishDate;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    @NotBlank @Pattern(regexp = "effective|pending|expired|repealed|unknown")
    private String validityStatus;
    @Size(max = 2000) private String summary;
    @Size(max = 20) private List<@Size(max = 64) String> keywords;
    @Size(max = 20) private List<@Size(max = 64) String> topicCodes;
}
