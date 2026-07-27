package com.kma.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class KmaRateLimitFilter extends OncePerRequestFilter {
    private final KmaSecurityProperties properties;
    private final SharedRateLimiter sharedRateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }
        boolean login = request.getRequestURI().equals("/api/v1/auth/login");
        int limit = login ? properties.getLoginRequestsPerMinute() : properties.getRequestsPerMinute();
        String key = key(request, login);
        SharedRateLimiter.Decision decision = sharedRateLimiter.consume(key, limit);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String key(HttpServletRequest request, boolean login) {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        if (principal != null) {
            return String.valueOf(principal.getSubjectId());
        }
        return (login ? "login:" : "anonymous:") + request.getRemoteAddr();
    }

}
