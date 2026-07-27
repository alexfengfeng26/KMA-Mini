package com.kma.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Configuration
public class KmaJwtConfiguration {

    @Bean
    SecretKey kmaLocalJwtKey(KmaSecurityProperties properties, Environment environment) {
        String configured = properties.getLocalSecret();
        byte[] key;
        if (configured == null || configured.isBlank()) {
            if (properties.localEnabled() && environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("生产环境必须配置 KMA_AUTH_JWT_SECRET");
            }
            key = new byte[32];
            new SecureRandom().nextBytes(key);
            log.warn("KMA 本地 JWT 密钥未配置，本次启动使用临时密钥；重启后现有令牌将失效");
        } else {
            key = decodeSecret(configured);
            if (key.length < 32) {
                throw new IllegalStateException("KMA_AUTH_JWT_SECRET 至少需要 32 字节");
            }
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder kmaJwtEncoder(SecretKey kmaLocalJwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(kmaLocalJwtKey));
    }

    @Bean
    JwtDecoder kmaJwtDecoder(SecretKey kmaLocalJwtKey, KmaSecurityProperties properties) {
        NimbusJwtDecoder local = NimbusJwtDecoder.withSecretKey(kmaLocalJwtKey)
            .macAlgorithm(MacAlgorithm.HS256).build();
        local.setJwtValidator(validators(properties.getLocalIssuer(), properties.getAudience()));
        return local;
    }

    @Bean
    PasswordEncoder kmaPasswordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    private OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT audience 不匹配", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
    }

    private byte[] decodeSecret(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
