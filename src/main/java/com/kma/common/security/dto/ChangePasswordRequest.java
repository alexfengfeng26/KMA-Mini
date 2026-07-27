package com.kma.common.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank @Size(max = 256) private String currentPassword;
    @NotBlank @Size(min = 12, max = 256) private String newPassword;
}
