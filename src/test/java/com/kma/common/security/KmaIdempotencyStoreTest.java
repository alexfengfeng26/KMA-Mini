package com.kma.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KmaIdempotencyStoreTest {
    @Mock private JdbcTemplate jdbc;
    private KmaIdempotencyStore store;

    @BeforeEach
    void setUp() {
        KmaSecurityProperties properties = new KmaSecurityProperties();
        properties.setIdempotencyTtlHours(2);
        properties.setIdempotencyLeaseSeconds(30);
        store = new KmaIdempotencyStore(jdbc, properties);
    }

    @Test
    void firstClaimAcquiresHashedCoordinates() {
        when(jdbc.update(contains("INSERT INTO"), any(), any(), any(), any(), any())).thenReturn(1);

        KmaIdempotencyStore.Claim claim = store.claim(
            "raw-secret-key", "post", "/api/v1/spaces?name=a");

        assertThat(claim.state()).isEqualTo(KmaIdempotencyStore.ClaimState.ACQUIRED);
        assertThat(claim.coordinates().method()).isEqualTo("POST");
        assertThat(claim.coordinates().keyHash()).hasSize(64).doesNotContain("raw-secret-key");
        assertThat(claim.coordinates().targetHash()).hasSize(64);
    }

    @Test
    void completedClaimIsReplayed() {
        when(jdbc.update(contains("INSERT INTO"), any(), any(), any(), any(), any())).thenReturn(0);
        KmaIdempotencyStore.Replay replay = new KmaIdempotencyStore.Replay(
            "completed", 201, "application/json", "ok".getBytes(StandardCharsets.UTF_8));
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any()))
            .thenReturn(List.of(replay));

        KmaIdempotencyStore.Claim claim = store.claim("key", "POST", "/target");

        assertThat(claim.state()).isEqualTo(KmaIdempotencyStore.ClaimState.REPLAY);
        assertThat(claim.replay().status()).isEqualTo(201);
    }

    @Test
    void staleProcessingClaimIsReclaimedButFreshClaimStaysInProgress() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any()))
            .thenReturn(List.of(new KmaIdempotencyStore.Replay("processing", 0, null, null)));
        when(jdbc.update(contains("INSERT INTO"), any(), any(), any(), any(), any())).thenReturn(0);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("UPDATE kma_idempotency_record"),
            any(), any(), any(), any(), any())).thenReturn(1, 0);

        assertThat(store.claim("stale", "POST", "/target").state())
            .isEqualTo(KmaIdempotencyStore.ClaimState.ACQUIRED);
        assertThat(store.claim("fresh", "POST", "/target").state())
            .isEqualTo(KmaIdempotencyStore.ClaimState.IN_PROGRESS);
    }

    @Test
    void completeAndReleaseUseOnlyHashedCoordinates() {
        KmaIdempotencyStore.Claim claim;
        when(jdbc.update(contains("INSERT INTO"), any(), any(), any(), any(), any())).thenReturn(1);
        claim = store.claim("key", "POST", "/target");

        store.complete(claim.coordinates(), 200, "x".repeat(300), new byte[]{1, 2});
        store.release(claim.coordinates());

        verify(jdbc, atLeastOnce()).update(anyString(), any(), any(), any());
        verify(jdbc).update(anyString(), eq(200), eq("x".repeat(255)), any(byte[].class),
            anyString(), eq("POST"), anyString());
    }
}
