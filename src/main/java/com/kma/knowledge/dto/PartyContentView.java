package com.kma.knowledge.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Portal and governance projection for a content version. */
@Data
public class PartyContentView {
    private Long contentId;
    private Long spaceId;
    private String spaceCode;
    private String spaceName;
    private String title;
    private String sourceTag;
    private String externalRef;
    private Long sourceVersion;
    private Boolean active;
    private String parseStatus;
    private String mimeType;
    private String contentType;
    private String documentNumber;
    private String issuingAuthority;
    private LocalDate publishDate;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String validityStatus;
    private String workflowStatus;
    private String reviewDecision;
    private String reviewNote;
    private Boolean online;
    private String summary;
    private List<String> keywords;
    private List<String> topicCodes;
    private Long reviewerId;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean favorite;
    private List<Map<String, Object>> sections;
    private List<Map<String, Object>> versions;
    private List<Map<String, Object>> related;
}
