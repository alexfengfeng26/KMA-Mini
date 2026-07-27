package com.kma.common.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class RoleUpsertRequest {
    @NotBlank @Pattern(regexp = "[a-z][a-z0-9:_-]{1,63}") private String roleCode;
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 512) private String description;
    @Pattern(regexp = "active|disabled") private String status = "active";
    @Size(max = 100) private Set<@Size(max = 64) String> permissions = new LinkedHashSet<>();
}
