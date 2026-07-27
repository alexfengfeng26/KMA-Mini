package com.kma.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KmaIdempotencyFilterTest {
    private final KmaIdempotencyStore store = mock(KmaIdempotencyStore.class);
    private final KmaSecurityProperties properties = new KmaSecurityProperties();
    private final KmaIdempotencyFilter filter = new KmaIdempotencyFilter(store, properties, new ObjectMapper());

    @BeforeEach
    void setIdentity() {
        KmaPrincipal principal = new KmaPrincipal();
        principal.setUserId(1L);
        principal.setUsername("tester");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistsSuccessfulWriteResponse() throws Exception {
        MockHttpServletRequest request = writeRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        KmaIdempotencyStore.Coordinates coordinates = new KmaIdempotencyStore.Coordinates(
            "key-hash", "POST", "target-hash");
        when(store.claim(eq("request-1"), eq("POST"), eq("/api/v1/spaces")))
            .thenReturn(KmaIdempotencyStore.Claim.acquired(coordinates));
        FilterChain chain = (req, res) -> {
            res.setContentType("application/json");
            res.getOutputStream().write("{\"code\":200}".getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":200}");
        verify(store).complete(eq(coordinates), eq(200), eq("application/json"), any(byte[].class));
    }

    @Test
    void replaysCompletedResponseWithoutCallingController() throws Exception {
        MockHttpServletRequest request = writeRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        KmaIdempotencyStore.Coordinates coordinates = new KmaIdempotencyStore.Coordinates(
            "key-hash", "POST", "target-hash");
        KmaIdempotencyStore.Replay replay = new KmaIdempotencyStore.Replay("completed", 201,
            "application/json", "{\"id\":7}".getBytes(StandardCharsets.UTF_8));
        when(store.claim(any(), any(), any()))
            .thenReturn(KmaIdempotencyStore.Claim.replay(coordinates, replay));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":7}");
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsConcurrentDuplicateAndExcludesAuthenticationWrites() throws Exception {
        MockHttpServletRequest request = writeRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        KmaIdempotencyStore.Coordinates coordinates = new KmaIdempotencyStore.Coordinates(
            "key-hash", "POST", "target-hash");
        when(store.claim(any(), any(), any()))
            .thenReturn(KmaIdempotencyStore.Claim.inProgress(coordinates));

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("Retry-After")).isEqualTo("300");
        assertThat(response.getContentAsString()).contains("正在处理中");

        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        login.addHeader("Idempotency-Key", "login-key");
        assertThat(filter.shouldNotFilter(login)).isTrue();
    }

    @Test
    void rejectsOversizedKeyInsteadOfSilentlyBypassingIdempotency() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/spaces");
        request.addHeader("Idempotency-Key", "x".repeat(257));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("256");
        verifyNoInteractions(store);
    }

    private MockHttpServletRequest writeRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/spaces");
        request.addHeader("Idempotency-Key", "request-1");
        return request;
    }
}
