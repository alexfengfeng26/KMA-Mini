package com.kma.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 将外部 OIDC/JWT claims 适配为与业务系统无关的 KMA 身份。 */
@Component
public class KmaJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
    private final KmaSecurityProperties properties;

    public KmaJwtAuthenticationConverter() {
        this(new KmaSecurityProperties());
    }

    @Autowired
    public KmaJwtAuthenticationConverter(KmaSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        KmaPrincipal principal = new KmaPrincipal();
        String subjectId = value(jwt, "subject_id", jwt.getSubject());
        principal.setSubjectId(subjectId);
        principal.setSubjectType(value(jwt, "subject_type", "user"));
        principal.setUsername(value(jwt, "preferred_username", subjectId));
        principal.setUserId(longValue(jwt.getClaim("user_id"), stableLong(subjectId)));
        principal.setOrgIds(strings(jwt.getClaim("org_ids")));
        List<String> organizationCodes = strings(jwt.getClaim("org_codes"));
        if (organizationCodes.isEmpty()) organizationCodes = principal.getOrgIds();
        principal.setOrganizationCodes(organizationCodes);
        principal.setOrgId(longValue(jwt.getClaim("org_id"), firstLong(principal.getOrgIds())));
        principal.setRoles(strings(jwt.getClaim("roles")).stream().collect(Collectors.toSet()));
        principal.setRoleIds(strings(jwt.getClaim("role_ids")).stream()
            .map(value -> longValue(value, null)).filter(value -> value != null).collect(Collectors.toSet()));

        Set<String> permissions = new LinkedHashSet<>(strings(jwt.getClaim("permissions")));
        permissions.addAll(strings(jwt.getClaim("authorities")));
        principal.setPermissions(KmaPermissionCatalog.expand(permissions));
        principal.setAuthorizationVersion(longValue(jwt.getClaim("auth_version"), null));
        principal.setTokenSource("local");
        principal.setClientId(value(jwt, "azp", value(jwt, "client_id", null)));
        principal.setMustChangePassword(Boolean.TRUE.equals(jwt.getClaim("must_change_password")));

        Collection<?> jwtAuthorities = authoritiesConverter.convert(jwt);
        Set<org.springframework.security.core.GrantedAuthority> authorities = new LinkedHashSet<>(principal.getAuthorities());
        if (jwtAuthorities != null) {
            jwtAuthorities.stream().map(item -> (org.springframework.security.core.GrantedAuthority) item)
                .forEach(authorities::add);
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt.getTokenValue(), authorities);
    }

    private String value(Jwt jwt, String claim, String fallback) {
        Object raw = jwt.getClaim(claim);
        return raw == null || raw.toString().isBlank() ? fallback : raw.toString();
    }

    private List<String> strings(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList();
        }
        return Arrays.stream(raw.toString().split(","))
            .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private Long firstLong(List<String> values) {
        return values.isEmpty() ? null : longValue(values.get(0), null);
    }

    private Long longValue(Object raw, Long fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.valueOf(raw.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Long stableLong(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(subjectId);
        } catch (NumberFormatException ignored) {
            return UUID.nameUUIDFromBytes(subjectId.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits()
                & Long.MAX_VALUE;
        }
    }
}
