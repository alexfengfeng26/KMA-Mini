package com.kma.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED = Set.of(
        "/api/v1/auth/me", "/api/v1/auth/change-password", "/api/v1/auth/logout");
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        if (principal != null && principal.isMustChangePassword()
            && request.getRequestURI().startsWith("/api/v1/")
            && !ALLOWED.contains(request.getRequestURI())
            && !isPortalBootstrap(request)) {
            response.setStatus(403); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":403,\"message\":\"首次登录必须先修改初始密码\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isPortalBootstrap(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "GET".equalsIgnoreCase(request.getMethod())
            && uri.startsWith("/api/v1/portal-sites/")
            && uri.endsWith("/bootstrap");
    }
}
