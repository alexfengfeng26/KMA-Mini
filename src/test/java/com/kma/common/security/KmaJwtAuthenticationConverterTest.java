package com.kma.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmaJwtAuthenticationConverterTest {

    private final KmaJwtAuthenticationConverter converter = new KmaJwtAuthenticationConverter();

    @Test
    void shouldMapStandardIdentityClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("external-user")
            .claim("org_ids", List.of("12", "18"))
            .claim("roles", List.of("editor"))
            .claim("role_ids", List.of("2", "5"))
            .claim("permissions", List.of("knowledge:qa:query"))
            .build();

        Authentication authentication = converter.convert(jwt);
        KmaPrincipal principal = (KmaPrincipal) authentication.getPrincipal();

        assertThat(principal.getSubjectId()).isEqualTo("external-user");
        assertThat(principal.getUserId()).isPositive();
        assertThat(principal.getOrgIds()).containsExactly("12", "18");
        assertThat(principal.getRoles()).containsExactly("editor");
        assertThat(principal.getRoleIds()).containsExactlyInAnyOrder(2L, 5L);
        assertThat(principal.getPermissions()).contains("knowledge:qa:query");
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void mapsClientCredentialsServiceIdentityContract() {
        Jwt jwt = Jwt.withTokenValue("service-token").header("alg", "RS256")
            .subject("opencourse-feed")
            .claim("subject_type", "service").claim("subject_id", "opencourse-feed")
            .claim("permissions", List.of("document:ingest")).build();
        KmaPrincipal principal = (KmaPrincipal) converter.convert(jwt).getPrincipal();
        assertThat(principal.getSubjectType()).isEqualTo("service");
        assertThat(principal.getSubjectId()).isEqualTo("opencourse-feed");
        assertThat(principal.getPermissions()).containsExactlyInAnyOrder("document:ingest", "document:read");
    }

    @Test
    void acceptsVerifiedLocalIssuerThatIsNotAUrl() {
        KmaSecurityProperties properties = new KmaSecurityProperties();
        properties.setLocalIssuer("kma-local");
        KmaJwtAuthenticationConverter localConverter = new KmaJwtAuthenticationConverter(properties);
        Jwt jwt = Jwt.withTokenValue("local-token").header("alg", "HS256")
            .subject("1").claim("iss", "kma-local")
            .claim("user_id", 1L).claim("auth_version", 3L).build();

        KmaPrincipal principal = (KmaPrincipal) localConverter.convert(jwt).getPrincipal();

        assertThat(principal.getTokenSource()).isEqualTo("local");
        assertThat(principal.getAuthorizationVersion()).isEqualTo(3L);
    }
}
