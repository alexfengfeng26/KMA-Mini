package com.kma.knowledge.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Editable metadata for a content draft. Null values retain the existing value. */
@Data
public class PartyContentMetadataRequest {
    @Size(max = 255) private String title;
    @Pattern(regexp = "party_constitution|policy|learning_material|grassroots_case|organization_system")
    private String contentType;
    @Size(max = 128) private String documentNumber;
    @Size(max = 255) private String issuingAuthority;
    private LocalDate publishDate;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private LocalDateTime scheduledOnlineAt;
    private LocalDateTime scheduledOfflineAt;
    @Size(max = 1000) private String scheduleNote;
    @Pattern(regexp = "effective|pending|expired|repealed|unknown")
    private String validityStatus;
    @Size(max = 2000) private String summary;
    @Size(max = 20) private List<@Size(max = 64) String> keywords;
    @Size(max = 20) private List<@Size(max = 64) String> topicCodes;
}
