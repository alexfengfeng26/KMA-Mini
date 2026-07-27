package com.kma.common.security;

import org.springframework.stereotype.Component;

/** 兼容迁移期权限表达式，最终权限来源为 KMA Principal。 */
@Component("ss")
public class KmaPermissionEvaluator {
    public boolean hasPermi(String permission) {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        return principal != null && KmaPermissionCatalog.has(principal.getPermissions(), permission);
    }

    public boolean hasAny(String... permissions) {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        if (principal == null || permissions == null) return false;
        for (String permission : permissions) {
            if (KmaPermissionCatalog.has(principal.getPermissions(), permission)) return true;
        }
        return false;
    }

    public boolean isPlatformAdmin() {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        return principal != null && principal.getPermissions().contains("kma:admin");
    }
}

