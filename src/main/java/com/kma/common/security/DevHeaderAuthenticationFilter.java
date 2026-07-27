package com.kma.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/** 仅限 local/dev 环境的可信请求头认证。生产必须使用 JWT/OIDC。 */
public class DevHeaderAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (!request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().equals("0:0:0:0:0:0:0:1")
            && !request.getRemoteAddr().equals("::1")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "dev 身份头只允许本机访问");
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            KmaPrincipal principal = new KmaPrincipal();
            principal.setUserId(parseLong(value(request, "X-KMA-User-Id", "1")));
            principal.setSubjectId(String.valueOf(principal.getUserId()));
            principal.setUsername(value(request, "X-KMA-Username", "local-admin"));
            principal.setOrgId(parseLong(value(request, "X-KMA-Org-Id", "1")));
            principal.setOrgIds(parseList(value(request, "X-KMA-Org-Ids", String.valueOf(principal.getOrgId()))));
            principal.setOrganizationCodes(parseList(value(request, "X-KMA-Org-Codes", "root")));
            principal.setRoleIds(parseLongSet(value(request, "X-KMA-Role-Ids", "1")));
            principal.setRoles(parseSet(value(request, "X-KMA-Roles", "kma-admin")));
            principal.setPermissions(KmaPermissionCatalog.expand(parseSet(value(request, "X-KMA-Authorities", "kma:admin"))));
            principal.setTokenSource("dev");
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    private String value(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private Long parseLong(String value) {
        try { return Long.valueOf(value); } catch (NumberFormatException ex) { return null; }
    }

    private Set<Long> parseLongSet(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).map(this::parseLong)
            .filter(item -> item != null).collect(Collectors.toSet());
    }

    private Set<String> parseSet(String value) {
        if (value == null || value.isBlank()) return Collections.emptySet();
        return Arrays.stream(value.split(",")).map(String::trim)
            .filter(item -> !item.isBlank()).collect(Collectors.toSet());
    }

    private java.util.List<String> parseList(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        return Arrays.stream(value.split(",")).map(String::trim)
            .filter(item -> !item.isBlank()).toList();
    }
}

