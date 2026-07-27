package com.kma.common.security.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class UserRolesRequest {
    @Size(max = 50) private Set<@Size(min = 2, max = 64) String> roles = new LinkedHashSet<>();
}
