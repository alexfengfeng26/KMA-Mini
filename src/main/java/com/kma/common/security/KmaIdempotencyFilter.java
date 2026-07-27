package com.kma.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.result.ApiResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Persists and replays successful write responses identified by Idempotency-Key. */
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
@Slf4j
public class KmaIdempotencyFilter extends OncePerRequestFilter {
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final KmaIdempotencyStore store;
    private final KmaSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public KmaIdempotencyFilter(KmaIdempotencyStore store, KmaSecurityProperties properties,
                                ObjectMapper objectMapper) {
        this.store = store;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String key = request.getHeader("Idempotency-Key");
        String uri = request.getRequestURI();
        return !WRITE_METHODS.contains(request.getMethod()) || key == null || key.isBlank()
            || uri.startsWith("/api/v1/auth/")
            || uri.contains("/stream");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (request.getHeader("Idempotency-Key").length() > 256) {
            writeError(response, 400, "Idempotency-Key 长度不能超过 256");
            return;
        }
        String target = request.getRequestURI()
            + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        KmaIdempotencyStore.Claim claim;
        try {
            claim = store.claim(request.getHeader("Idempotency-Key"), request.getMethod(), target);
        } catch (RuntimeException ex) {
            log.error("幂等记录占用失败, method={}, target={}", request.getMethod(), target, ex);
            writeError(response, 503, "幂等服务暂不可用，请稍后重试");
            return;
        }
        if (claim.state() == KmaIdempotencyStore.ClaimState.REPLAY) {
            replay(response, claim.replay());
            return;
        }
        if (claim.state() == KmaIdempotencyStore.ClaimState.IN_PROGRESS) {
            response.setHeader("Retry-After", String.valueOf(properties.getIdempotencyLeaseSeconds()));
            writeError(response, 409, "相同 Idempotency-Key 的请求正在处理中");
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
            byte[] body = wrapper.getContentAsByteArray();
            if (wrapper.getStatus() >= 200 && wrapper.getStatus() < 300
                && body.length <= properties.getIdempotencyMaxResponseBytes()) {
                store.complete(claim.coordinates(), wrapper.getStatus(), wrapper.getContentType(), body);
            } else {
                store.release(claim.coordinates());
            }
        } catch (IOException | ServletException | RuntimeException ex) {
            store.release(claim.coordinates());
            throw ex;
        } finally {
            wrapper.copyBodyToResponse();
        }
    }

    private void replay(HttpServletResponse response, KmaIdempotencyStore.Replay replay) throws IOException {
        response.setStatus(replay.status());
        response.setHeader("Idempotency-Replayed", "true");
        if (replay.contentType() != null) {
            response.setContentType(replay.contentType());
        }
        if (replay.body() != null) {
            response.getOutputStream().write(replay.body());
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResult.fail(status, message));
    }
}
