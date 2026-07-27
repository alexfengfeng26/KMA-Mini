package com.kma.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Set;

/** KMA 无状态安全配置。 */
@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/refresh"
    );

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             KmaSecurityProperties properties,
                                             Environment environment,
                                             KmaRateLimitFilter rateLimitFilter,
                                             AuthorizationVersionFilter authorizationVersionFilter,
                                             MustChangePasswordFilter mustChangePasswordFilter,
                                             KmaIdempotencyFilter idempotencyFilter,
                                             KmaJwtAuthenticationConverter jwtConverter) throws Exception {
        boolean devMode = "dev".equalsIgnoreCase(properties.getMode());
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness",
                    "/api/v1/auth/login", "/api/v1/auth/refresh", "/portal-sandbox/**").permitAll();
                if (devMode) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                }
                auth.anyRequest().authenticated();
            });

        if (devMode) {
            if (!environment.acceptsProfiles(Profiles.of("dev", "test"))) {
                throw new IllegalStateException("dev 安全模式只能在 dev/test Profile 中启用");
            }
            http.addFilterBefore(new DevHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        } else {
            DefaultBearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();
            http.oauth2ResourceServer(resource -> resource
                .bearerTokenResolver(request -> isPublicAuthPath(request.getRequestURI())
                    ? null : bearerTokenResolver.resolve(request))
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        }
        http.addFilterAfter(authorizationVersionFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(rateLimitFilter, AuthorizationVersionFilter.class);
        http.addFilterAfter(mustChangePasswordFilter, KmaRateLimitFilter.class);
        http.addFilterAfter(idempotencyFilter, MustChangePasswordFilter.class);
        return http.build();
    }

    static boolean isPublicAuthPath(String requestUri) {
        return PUBLIC_AUTH_PATHS.contains(requestUri);
    }
}

