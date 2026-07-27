package com.kma.common.security.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.List;

@Data
@Builder
public class AuthTokenResponse {
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private Long userId;
    private String username;
    private String displayName;
    private boolean mustChangePassword;
    private Set<String> roles;
    private Set<String> permissions;
    private List<String> organizationCodes;
    private Long authorizationVersion;
}
