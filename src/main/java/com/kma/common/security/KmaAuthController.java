package com.kma.common.security;

import com.kma.common.result.ApiResult;
import com.kma.common.security.dto.AuthTokenResponse;
import com.kma.common.security.dto.LoginRequest;
import com.kma.common.security.dto.ChangePasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class KmaAuthController {
    private static final String REFRESH_COOKIE = "KMA_REFRESH";
    private final KmaLocalAuthService authService;
    private final KmaSecurityProperties properties;
    private final Environment environment;

    @PostMapping("/login")
    public ApiResult<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest,
                                               HttpServletResponse response) {
        KmaLocalAuthService.TokenPair tokens = authService.login(request);
        setRefreshCookie(response, servletRequest.isSecure(), tokens.refreshToken());
        return ApiResult.success(tokens.response());
    }

    @PostMapping("/refresh")
    public ApiResult<AuthTokenResponse> refresh(
        @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletRequest servletRequest,
        HttpServletResponse response) {
        KmaLocalAuthService.TokenPair tokens = authService.refresh(refreshToken);
        setRefreshCookie(response, servletRequest.isSecure(), tokens.refreshToken());
        return ApiResult.success(tokens.response());
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<Void> logout(
        @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletRequest servletRequest,
        HttpServletResponse response) {
        authService.logout(refreshToken);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true).secure(secureCookie(servletRequest)).sameSite("Strict")
            .path("/api/v1/auth").maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ApiResult.success();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<KmaPrincipal> me() {
        return ApiResult.success(KmaIdentityContext.getLoginUser());
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                          HttpServletRequest servletRequest, HttpServletResponse response) {
        authService.changePassword(request);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true)
            .secure(secureCookie(servletRequest)).sameSite("Strict").path("/api/v1/auth")
            .maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ApiResult.success();
    }

    private void setRefreshCookie(HttpServletResponse response, boolean secure, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
            .httpOnly(true).secure(secure || environment.acceptsProfiles(Profiles.of("prod"))).sameSite("Strict")
            .path("/api/v1/auth").maxAge(Duration.ofDays(properties.getRefreshTtlDays())).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean secureCookie(HttpServletRequest request) {
        return request.isSecure() || environment.acceptsProfiles(Profiles.of("prod"));
    }
}
