package com.kma.common.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 与接入业务无关的最小身份上下文。 */
@Data
public class KmaPrincipal implements UserDetails {
    private Long userId;
    private String subjectId;
    private String subjectType = "user";
    private String username;
    private String password = "";
    private Long orgId;
    private List<String> orgIds = Collections.emptyList();
    private Set<Long> roleIds = Collections.emptySet();
    private Set<String> roles = Collections.emptySet();
    private Set<String> permissions = Collections.emptySet();
    private List<String> organizationCodes = Collections.emptyList();
    private Long authorizationVersion;
    private String tokenSource = "oidc";
    private String clientId;
    private boolean mustChangePassword;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream().<GrantedAuthority>map(value -> () -> value).toList();
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}

