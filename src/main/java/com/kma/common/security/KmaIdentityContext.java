package com.kma.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前请求身份访问入口。 */
public final class KmaIdentityContext {
    private KmaIdentityContext() {}

    public static KmaPrincipal getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof KmaPrincipal principal
            ? principal : null;
    }

    public static Long getUserId() {
        KmaPrincipal principal = getLoginUser();
        return principal == null ? null : principal.getUserId();
    }

    public static String getUsername() {
        KmaPrincipal principal = getLoginUser();
        return principal == null ? null : principal.getUsername();
    }

    public static boolean isSuperAdmin() {
        KmaPrincipal principal = getLoginUser();
        return principal != null && (principal.getPermissions().contains("kma:admin")
            || principal.getRoles().contains("kma-admin"));
    }
}

