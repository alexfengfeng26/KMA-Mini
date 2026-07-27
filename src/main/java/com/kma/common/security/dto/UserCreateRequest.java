package com.kma.common.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserCreateRequest {
    @NotBlank @Size(max = 128)
    private String username;
    @Size(max = 128)
    private String displayName;
    @NotBlank @Size(min = 12, max = 256)
    private String initialPassword;
    @Size(max = 20)
    private List<@Size(max = 64) String> roles = List.of();
}
