package com.kma.common.security.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** Filter contract for the authorization-governance audit workbench. */
@Data
public class SecurityAuditQuery {
    @Min(1) private int pageNum = 1;
    @Min(1) @Max(100) private int pageSize = 20;
    private String username;
    private String resource;
    private String eventType;
    private String action;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime to;
}
