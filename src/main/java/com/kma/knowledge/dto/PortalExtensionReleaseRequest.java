package com.kma.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Platform CI registration request for an immutable, signed portal extension release. */
@Data
public class PortalExtensionReleaseRequest {
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$")
    private String extensionId;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$")
    private String version;

    @NotBlank
    @Size(max = 128)
    private String displayName;

    @NotBlank
    @Pattern(regexp = "^/portal-extensions/[a-z][a-z0-9_-]{1,63}/[A-Za-z0-9._-]+/index\\.html$")
    private String entryUrl;

    @NotBlank
    @Size(max = 128)
    private String integrityHash;

    @NotBlank
    @Size(max = 256)
    private String signature;

    @Size(max = 32)
    private String minFrontendVersion;

    @NotNull
    private JsonNode manifest;
}
