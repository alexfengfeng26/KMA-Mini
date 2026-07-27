package com.kma.common.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** PostgreSQL-backed fixed-window limiter shared by every API instance. */
@Service
public class SharedRateLimiter {
    private final JdbcTemplate jdbc;

    public SharedRateLimiter(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Decision consume(String rawKey, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        Integer used = jdbc.queryForObject("""
            INSERT INTO kma_rate_limit_bucket(bucket_key,window_start,used,expires_at)
            VALUES (?,?,1,now()+interval '3 minutes')
            ON CONFLICT (bucket_key,window_start) DO UPDATE SET
                used=kma_rate_limit_bucket.used+1,expires_at=EXCLUDED.expires_at
            RETURNING used
            """, Integer.class, hash(rawKey), minute);
        if (minute % 10 == 0 && used != null && used == 1) {
            jdbc.update("DELETE FROM kma_rate_limit_bucket WHERE expires_at<now()");
        }
        int count = used == null ? limit + 1 : used;
        return new Decision(count <= limit, Math.max(0, limit - count), limit);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成限流键", ex);
        }
    }

    public record Decision(boolean allowed, int remaining, int limit) {}
}
