package com.kma.knowledge.storage;

import com.kma.common.exception.KmaException;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
class StorageLifecycleServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private KnowledgeStorage storage;
    @Mock private SecurityAuditService auditService;
    private KnowledgeProperties properties;
    private StorageLifecycleService service;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        properties.getStorage().setType("local");
        properties.getStorage().setOrphanGraceHours(6);
        service = new StorageLifecycleService(jdbc, storage, properties, auditService);
    }

    @Test
    void registersBindsAndMarksUnreferencedObjects() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("a.txt"),
            eq("local"), eq("SHA-256"), eq("abc"), eq(12L))).thenReturn(7L);
        assertThat(call(() -> service.registerStored("a.txt", 12L, "abc"))).isEqualTo(7L);

        when(jdbc.update(org.mockito.ArgumentMatchers.contains("status='active'"), eq(7L)))
            .thenReturn(1);
        run(() -> service.bindDocument(7L, 11L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("knowledge_doc"),
            eq(7L), eq(11L));
        run(() -> service.markOrphanIfUnreferenced(7L));
        run(() -> service.markOrphanIfUnreferenced(null));
    }

    @Test
    void refusesBindingWhenDeletionAlreadyStarted() {
        when(jdbc.update(anyString(), eq(7L))).thenReturn(0);
        assertThatThrownBy(() -> run(() -> service.bindDocument(7L, 11L)))
            .isInstanceOf(KmaException.class).extracting("code").isEqualTo(409);
    }

    @Test
    void reconciliationClassifiesActiveOrphanCorruptMissingAndDiscoveredObjects() throws Exception {
        List<Map<String, Object>> rows = List.of(
            row(1L, "active", "aaa", true),
            row(2L, "orphan", "bbb", false),
            row(3L, "corrupt", "expected", true),
            row(4L, "missing", null, true));
        when(jdbc.queryForList(anyString(), anyInt())).thenReturn(rows);
        when(storage.inspect("active")).thenReturn(new StorageObjectMetadata("active", 10, "aaa", "SHA-256"));
        when(storage.inspect("orphan")).thenReturn(new StorageObjectMetadata("orphan", 20, "bbb", "SHA-256"));
        when(storage.inspect("corrupt")).thenReturn(new StorageObjectMetadata("corrupt", 30, "actual", "SHA-256"));
        when(storage.inspect("missing")).thenThrow(new IOException("not found"));
        when(storage.list(1000)).thenReturn(List.of(
            new StorageObjectMetadata("active", 10, "aaa", "SHA-256"),
            new StorageObjectMetadata("discovered", 40, null, null)));

        Map<String, Object> result = service.reconcile();

        assertThat(result).containsEntry("active", 1)
            .containsEntry("orphan", 1)
            .containsEntry("corrupt", 1)
            .containsEntry("missing", 1)
            .containsEntry("discovered", 1);
        verify(auditService).record(eq("storage_reconciliation"), eq("warning"), eq("completed"),
            eq("storage"), any(), any(), eq(result));
    }

    @Test
    void physicalListingFailureDoesNotAbortReconciliation() throws Exception {
        when(jdbc.queryForList(anyString(), anyInt())).thenReturn(List.of());
        when(storage.list(1000)).thenThrow(new IOException("provider down"));
        assertThat(service.reconcile()).containsEntry("discovered", 0);
    }

    @Test
    void cleanupDeletesClaimedObjectsAndRetainsFailuresForRetry() throws Exception {
        List<Map<String, Object>> candidates = List.of(
            Map.of("object_id", 1L, "location", "ok"),
            Map.of("object_id", 2L, "location", "bad"),
            Map.of("object_id", 3L, "location", "not-claimed"));
        when(jdbc.queryForList(anyString(), anyInt())).thenReturn(candidates);
        lenient().when(jdbc.update(org.mockito.ArgumentMatchers.contains("status='deleting'"),
            eq(1L), eq(1L))).thenReturn(1);
        lenient().when(jdbc.update(org.mockito.ArgumentMatchers.contains("status='deleting'"),
            eq(2L), eq(2L))).thenReturn(1);
        lenient().when(jdbc.update(org.mockito.ArgumentMatchers.contains("status='deleting'"),
            eq(3L), eq(3L))).thenReturn(0);
        doNothing().when(storage).delete("ok");
        lenient().doThrow(new IOException("locked")).when(storage).delete("bad");

        run(service::cleanupNow);

        verify(storage).delete("ok");
        verify(storage).delete("bad");
        verify(storage, never()).delete("not-claimed");
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status='delete_failed'"),
            eq("locked"), eq(2L));
    }

    @Test
    void scheduledJobsHonorLifecycleSwitch() {
        properties.getStorage().setLifecycleEnabled(false);
        service.scheduledReconcile();
        service.cleanupDue();
        verify(jdbc, never()).queryForList(anyString(), anyInt());

        properties.getStorage().setLifecycleEnabled(true);
        when(jdbc.queryForList(anyString(), anyInt())).thenReturn(List.of());
        service.scheduledReconcile();
        service.cleanupDue();
        verify(jdbc, org.mockito.Mockito.atLeast(2)).queryForList(anyString(), anyInt());
    }

    @Test
    void listsObjectsWithOptionalStatusAndBoundedLimit() {
        when(jdbc.queryForList(anyString(), eq(""), eq(""), eq(500)))
            .thenReturn(List.of(Map.of("object_id", 1L)));
        assertThat(call(() -> service.list(null, 9999))).hasSize(1);

        when(jdbc.queryForList(anyString(), eq("orphan"), eq("orphan"), eq(1)))
            .thenReturn(List.of());
        assertThat(call(() -> service.list("orphan", 0))).isEmpty();
    }

    private Map<String, Object> row(long id, String location, String checksum, boolean referenced) {
        java.util.HashMap<String, Object> row = new java.util.HashMap<>();
        row.put("object_id", id);
        row.put("location", location);
        row.put("checksum_algorithm", "SHA-256");
        row.put("checksum", checksum);
        row.put("status", "active");
        row.put("referenced", referenced);
        return row;
    }

    private <T> T call(Callable<T> action) {
        try {
            return action.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void run(Runnable action) {
        action.run();
    }
}
