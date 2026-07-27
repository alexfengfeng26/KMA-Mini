package com.kma.common.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** PostgreSQL-backed lease and response store for write-request idempotency. */
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class KmaIdempotencyStore {
    private final JdbcTemplate jdbcTemplate;
    private final KmaSecurityProperties properties;

    public KmaIdempotencyStore(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbcTemplate,
                               KmaSecurityProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public Claim claim(String key, String method, String target) {
        Coordinates coordinates = coordinates(key, method, target);
        deleteExpired(coordinates);
        int inserted = jdbcTemplate.update("""
            INSERT INTO kma_idempotency_record
                (key_hash, http_method, target_hash, request_target, state, expires_at)
            VALUES (?, ?, ?, ?, 'processing', now() + (? * interval '1 second'))
            ON CONFLICT (key_hash, http_method, target_hash) DO NOTHING
            """, coordinates.keyHash(), coordinates.method(), coordinates.targetHash(),
            truncate(target, 512), properties.getIdempotencyTtlHours() * 3600L);
        if (inserted == 1) {
            return Claim.acquired(coordinates);
        }

        List<Replay> existing = jdbcTemplate.query("""
            SELECT state, response_status, response_content_type, response_body
              FROM kma_idempotency_record
             WHERE key_hash = ? AND http_method = ? AND target_hash = ?
            """, (rs, rowNum) -> new Replay(rs.getString("state"), rs.getInt("response_status"),
                rs.getString("response_content_type"), rs.getBytes("response_body")),
            coordinates.keyHash(), coordinates.method(), coordinates.targetHash());
        if (!existing.isEmpty() && "completed".equals(existing.get(0).state())) {
            return Claim.replay(coordinates, existing.get(0));
        }

        int reclaimed = jdbcTemplate.update("""
            UPDATE kma_idempotency_record
               SET update_time = now(), expires_at = now() + (? * interval '1 second')
             WHERE key_hash = ? AND http_method = ? AND target_hash = ?
               AND state = 'processing'
               AND update_time < now() - (? * interval '1 second')
            """, properties.getIdempotencyTtlHours() * 3600L, coordinates.keyHash(),
            coordinates.method(), coordinates.targetHash(), properties.getIdempotencyLeaseSeconds());
        return reclaimed == 1 ? Claim.acquired(coordinates) : Claim.inProgress(coordinates);
    }

    public void complete(Coordinates coordinates, int status, String contentType, byte[] body) {
        jdbcTemplate.update("""
            UPDATE kma_idempotency_record
               SET state = 'completed', response_status = ?, response_content_type = ?, response_body = ?,
                   update_time = now()
             WHERE key_hash = ? AND http_method = ? AND target_hash = ?
               AND state = 'processing'
            """, status, truncate(contentType, 255), body, coordinates.keyHash(),
            coordinates.method(), coordinates.targetHash());
    }

    public void release(Coordinates coordinates) {
        jdbcTemplate.update("""
            DELETE FROM kma_idempotency_record
             WHERE key_hash = ? AND http_method = ? AND target_hash = ?
               AND state = 'processing'
            """, coordinates.keyHash(), coordinates.method(), coordinates.targetHash());
    }

    private void deleteExpired(Coordinates coordinates) {
        jdbcTemplate.update("""
            DELETE FROM kma_idempotency_record
             WHERE key_hash = ? AND http_method = ? AND target_hash = ?
               AND expires_at < now()
            """, coordinates.keyHash(), coordinates.method(), coordinates.targetHash());
    }

    private Coordinates coordinates(String key, String method, String target) {
        return new Coordinates(sha256(key), method.toUpperCase(), sha256(target));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record Coordinates(String keyHash, String method, String targetHash) {}

    public record Replay(String state, int status, String contentType, byte[] body) {}

    public record Claim(ClaimState state, Coordinates coordinates, Replay replay) {
        static Claim acquired(Coordinates coordinates) {
            return new Claim(ClaimState.ACQUIRED, coordinates, null);
        }

        static Claim replay(Coordinates coordinates, Replay replay) {
            return new Claim(ClaimState.REPLAY, coordinates, replay);
        }

        static Claim inProgress(Coordinates coordinates) {
            return new Claim(ClaimState.IN_PROGRESS, coordinates, null);
        }
    }

    public enum ClaimState { ACQUIRED, REPLAY, IN_PROGRESS }
}
