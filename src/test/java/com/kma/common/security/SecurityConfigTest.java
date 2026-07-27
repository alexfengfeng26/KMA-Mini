package com.kma.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void publicAuthenticationEndpointsIgnoreBearerTokens() {
        assertThat(SecurityConfig.isPublicAuthPath("/api/v1/auth/login")).isTrue();
        assertThat(SecurityConfig.isPublicAuthPath("/api/v1/auth/refresh")).isTrue();
        assertThat(SecurityConfig.isPublicAuthPath("/api/v1/auth/oidc/config")).isFalse();
        assertThat(SecurityConfig.isPublicAuthPath("/api/v1/auth/oidc/token")).isFalse();
    }

    @Test
    void protectedAuthenticationEndpointsStillRequireBearerValidation() {
        assertThat(SecurityConfig.isPublicAuthPath("/api/v1/auth/me")).isFalse();
        assertThat(SecurityConfig.isPublicAuthPath("/api/v1/auth/logout")).isFalse();
        assertThat(SecurityConfig.isPublicAuthPath("/api/v1/auth/change-password")).isFalse();
        assertThat(SecurityConfig.isPublicAuthPath("/api/v1/auth/login/other")).isFalse();
    }
}
