package com.kma.common.security;

import com.kma.common.exception.KmaException;
import com.kma.common.result.KmaResultCode;
import com.kma.common.security.dto.AuthTokenResponse;
import com.kma.common.security.dto.LoginRequest;
import com.kma.common.security.dto.ChangePasswordRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class KmaLocalAuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final KmaSecurityProperties properties;
    private final SecurityAuditService audit;
    private final SecureRandom secureRandom = new SecureRandom();

    public KmaLocalAuthService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                               PasswordEncoder passwordEncoder,
                               JwtEncoder jwtEncoder,
                               KmaSecurityProperties properties) {
        this(jdbc, passwordEncoder, jwtEncoder, properties, null);
    }

    @Autowired
    public KmaLocalAuthService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                               PasswordEncoder passwordEncoder,
                               JwtEncoder jwtEncoder,
                               KmaSecurityProperties properties,
                               SecurityAuditService audit) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public TokenPair login(LoginRequest request) {
        if (!properties.localEnabled()) {
            throw new KmaException(KmaResultCode.UNAUTHORIZED, "本地账号登录未启用");
        }
        UserAccount account = findUser(request.getUsername());
        if (account == null || account.passwordHash() == null
            || !passwordEncoder.matches(request.getPassword(), account.passwordHash())) {
            auditBestEffort("login_failure", "warning", "auth.login.failed", "user:" + request.getUsername(),
                List.of("INVALID_CREDENTIALS"), java.util.Map.of());
            throw new KmaException(KmaResultCode.UNAUTHORIZED, "用户名或密码不正确");
        }
        if (!"active".equalsIgnoreCase(account.userStatus())) {
            throw new KmaException(KmaResultCode.UNAUTHORIZED, "ACCOUNT_DISABLED");
        }
        jdbc.update("UPDATE kma_user SET last_login_time = now(), update_time = now() WHERE user_id=?",
            account.userId());
        TokenPair tokens = issue(account, null);
        auditBestEffort("login_success", "info", "auth.login", "user:" + account.userId(),
            List.of(), java.util.Map.of());
        return tokens;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", noRollbackFor = RefreshTokenReuseDetected.class)
    public TokenPair refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new KmaException(KmaResultCode.UNAUTHORIZED, "刷新令牌缺失");
        }
        String hash = sha256(rawRefreshToken);
        List<RefreshRecord> records = jdbc.query("""
            SELECT token_id, user_id, expires_at, family_id, revoked_at, replaced_by
            FROM kma_refresh_token
            WHERE token_hash = ?
            FOR UPDATE
            """, (rs, row) -> new RefreshRecord(
            rs.getObject("token_id", UUID.class), rs.getLong("user_id"),
            rs.getTimestamp("expires_at").toLocalDateTime(), rs.getObject("family_id", UUID.class),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toLocalDateTime(),
            rs.getObject("replaced_by", UUID.class)), hash);
        if (records.isEmpty()) {
            throw new KmaException(KmaResultCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }
        RefreshRecord record = records.get(0);
        if (record.revokedAt() != null || record.replacedBy() != null) {
            if (record.replacedBy() != null) {
                jdbc.update("""
                    UPDATE kma_refresh_token SET revoked_at=COALESCE(revoked_at,now()),reuse_detected_at=now()
                    WHERE family_id=?
                    """, record.familyId());
                jdbc.update("""
                    UPDATE kma_user SET auth_version=auth_version+1,update_time=now()
                    WHERE user_id=?
                    """, record.userId());
                if (audit != null) audit.recordRequired("token_reuse", "critical", "auth.refresh.reuse",
                    "user:" + record.userId(), java.util.Map.of(),
                    java.util.Map.of("familyRevoked", true, "authorizationVersionIncremented", true),
                    java.util.Map.of("familyId", record.familyId().toString()));
                throw new RefreshTokenReuseDetected();
            }
            throw new KmaException(KmaResultCode.UNAUTHORIZED, "刷新令牌无效或已撤销");
        }
        if (record.expiresAt().isBefore(LocalDateTime.now())) {
            throw new KmaException(KmaResultCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }
        UserAccount account = findUser(record.userId());
        if (account == null || !"active".equalsIgnoreCase(account.userStatus())) {
            throw new KmaException(KmaResultCode.UNAUTHORIZED, "ACCOUNT_DISABLED");
        }
        TokenPair replacement = issue(account, record.familyId());
        jdbc.update("""
            UPDATE kma_refresh_token SET revoked_at=now(),replaced_by=?,last_used_time=now(),used_at=now()
            WHERE token_id=?
            """,
            replacement.refreshTokenId(), record.tokenId());
        return replacement;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            KmaPrincipal principal = KmaIdentityContext.getLoginUser();
            if (principal == null || principal.getUserId() == null) throw new KmaException(401, "未登录");
            int revoked = jdbc.update("""
                UPDATE kma_refresh_token SET revoked_at=COALESCE(revoked_at,now())
                WHERE family_id=(SELECT family_id FROM kma_refresh_token WHERE token_hash=?)
                  AND user_id=?
                """, sha256(rawRefreshToken), principal.getUserId());
            if (revoked > 0 && audit != null) audit.recordRequired("token_revocation", "info", "auth.logout",
                "user:" + principal.getUserId(), java.util.Map.of(),
                java.util.Map.of("refreshTokenFamilyRevoked", true), java.util.Map.of());
        }
    }

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void changePassword(ChangePasswordRequest request) {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        if (principal == null || principal.getUserId() == null) throw new KmaException(401, "未登录");
        UserAccount account = findUser(principal.getUserId());
        if (account == null || account.passwordHash() == null
            || !passwordEncoder.matches(request.getCurrentPassword(), account.passwordHash())) {
            throw new KmaException(400, "当前密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), account.passwordHash())) {
            throw new KmaException(400, "新密码不能与当前密码相同");
        }
        jdbc.update("""
            UPDATE kma_user SET password_hash=?,must_change_password=FALSE,
                auth_version=auth_version+1,update_time=now()
            WHERE user_id=? AND identity_provider='local'
            """, passwordEncoder.encode(request.getNewPassword()), principal.getUserId());
        jdbc.update("""
            UPDATE kma_refresh_token SET revoked_at=now()
            WHERE user_id=? AND revoked_at IS NULL
            """, principal.getUserId());
        if (audit != null) audit.recordRequired("identity_change", "warning", "user.password.change",
            "user:" + principal.getUserId(), java.util.Map.of(),
            java.util.Map.of("mustChangePassword", false, "refreshTokensRevoked", true), java.util.Map.of());
    }

    private TokenPair issue(UserAccount account, UUID existingFamilyId) {
        Set<String> roles = new HashSet<>(jdbc.queryForList("""
            SELECT r.role_code FROM kma_role r
            JOIN kma_user_role ur ON ur.role_id = r.role_id
            WHERE ur.user_id = ? AND r.status='active'
            """, String.class, account.userId()));
        Set<Long> roleIds = new HashSet<>(jdbc.queryForList("""
            SELECT r.role_id FROM kma_role r
            JOIN kma_user_role ur ON ur.role_id = r.role_id
            WHERE ur.user_id = ? AND r.status='active'
            """, Long.class, account.userId()));
        Set<String> permissions = new HashSet<>(jdbc.queryForList("""
            SELECT DISTINCT rp.permission_code FROM kma_role_permission rp
            JOIN kma_user_role ur ON ur.role_id = rp.role_id
            JOIN kma_role r ON r.role_id=ur.role_id AND r.status='active'
            JOIN kma_permission p ON p.permission_code=rp.permission_code AND p.enabled=TRUE
            WHERE ur.user_id = ?
            """, String.class, account.userId()));
        permissions = new HashSet<>(KmaPermissionCatalog.expand(permissions));
        List<String> organizationCodes = jdbc.queryForList("""
            SELECT o.org_code FROM kma_user_org uo
            JOIN kma_org o ON o.org_id=uo.org_id
            WHERE uo.user_id=? AND o.status='active'
            ORDER BY uo.primary_org DESC,o.sort_order,o.org_code
            """, String.class, account.userId());

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getAccessTtlMinutes() * 60L);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.getLocalIssuer())
            .subject(String.valueOf(account.userId()))
            .audience(List.of(properties.getAudience()))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim("user_id", account.userId())
            .claim("subject_id", String.valueOf(account.userId()))
            .claim("subject_type", "user")
            .claim("preferred_username", account.username())
            .claim("roles", roles)
            .claim("role_ids", roleIds)
            .claim("permissions", permissions)
            .claim("org_codes", organizationCodes)
            .claim("auth_version", account.authVersion())
            .claim("token_source", "local")
            .claim("must_change_password", account.mustChangePassword())
            .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        UUID refreshTokenId = UUID.randomUUID();
        UUID familyId = existingFamilyId == null ? refreshTokenId : existingFamilyId;
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusDays(properties.getRefreshTtlDays());
        jdbc.update("""
            INSERT INTO kma_refresh_token(token_id, user_id, token_hash, expires_at, family_id)
            VALUES (?, ?, ?, ?, ?)
            """, refreshTokenId, account.userId(), sha256(refreshToken), refreshExpiresAt, familyId);

        AuthTokenResponse response = AuthTokenResponse.builder()
            .accessToken(accessToken).tokenType("Bearer")
            .expiresIn(properties.getAccessTtlMinutes() * 60L)
            .userId(account.userId())
            .username(account.username())
            .displayName(account.displayName()).mustChangePassword(account.mustChangePassword())
            .roles(roles).permissions(permissions).organizationCodes(organizationCodes)
            .authorizationVersion(account.authVersion()).build();
        return new TokenPair(response, refreshToken, refreshTokenId);
    }

    private UserAccount findUser(String username) {
        List<UserAccount> users = jdbc.query("""
            SELECT u.user_id,u.username,u.display_name,u.password_hash,
                   u.status AS user_status,
                   u.must_change_password,u.auth_version
            FROM kma_user u
            WHERE lower(u.username) = lower(?) AND u.identity_provider = 'local'
            """, (rs, row) -> mapUser(rs), username);
        return users.isEmpty() ? null : users.get(0);
    }

    private UserAccount findUser(Long userId) {
        List<UserAccount> users = jdbc.query("""
            SELECT u.user_id,u.username,u.display_name,u.password_hash,
                   u.status AS user_status,
                   u.must_change_password,u.auth_version
            FROM kma_user u
            WHERE u.user_id = ?
            """, (rs, row) -> mapUser(rs), userId);
        return users.isEmpty() ? null : users.get(0);
    }

    private UserAccount mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserAccount(rs.getLong("user_id"), rs.getString("username"),
            rs.getString("display_name"), rs.getString("password_hash"), rs.getString("user_status"),
            rs.getBoolean("must_change_password"), rs.getLong("auth_version"));
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成令牌摘要", ex);
        }
    }

    private void auditBestEffort(String eventType, String severity, String action, String resource,
                                 List<String> flags, java.util.Map<String, Object> details) {
        if (audit != null) audit.recordBestEffort(eventType, severity, action, resource, null, flags, details);
    }

    public record TokenPair(AuthTokenResponse response, String refreshToken, UUID refreshTokenId) {}
    private record UserAccount(Long userId, String username, String displayName,
                               String passwordHash, String userStatus,
                               boolean mustChangePassword, long authVersion) {}
    private record RefreshRecord(UUID tokenId, Long userId, LocalDateTime expiresAt,
                                 UUID familyId, LocalDateTime revokedAt, UUID replacedBy) {}
    private static final class RefreshTokenReuseDetected extends KmaException {
        private RefreshTokenReuseDetected() { super(401, "REFRESH_TOKEN_REUSED"); }
    }
}
