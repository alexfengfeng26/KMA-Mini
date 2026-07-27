package com.kma.knowledge.storage;

import com.kma.common.exception.KmaException;
import com.kma.common.result.PageResult;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.config.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageLifecycleService {
    @Qualifier("knowledgeJdbcTemplate")
    private final JdbcTemplate jdbc;
    private final KnowledgeStorage storage;
    private final KnowledgeProperties properties;
    private final SecurityAuditService auditService;

    public Long registerStored(String location, long sizeBytes, String sha256) {
        return jdbc.queryForObject("""
            INSERT INTO knowledge_storage_object
                (location,storage_type,checksum_algorithm,checksum,size_bytes,status,last_reconciled_at)
            VALUES (?,?,?,?,?,'active',now())
            ON CONFLICT (location) DO UPDATE SET
                checksum_algorithm=EXCLUDED.checksum_algorithm,checksum=EXCLUDED.checksum,
                size_bytes=EXCLUDED.size_bytes,status='active',delete_after=NULL,deleted_at=NULL,
                error_message=NULL,last_reconciled_at=now(),update_time=now()
            RETURNING object_id
            """, Long.class, location, properties.getStorage().getType(), "SHA-256", sha256, sizeBytes);
    }

    public void bindDocument(Long objectId, Long docId) {
        int active = jdbc.update("""
            UPDATE knowledge_storage_object SET status='active',delete_after=NULL,error_message=NULL,update_time=now()
            WHERE object_id=? AND status NOT IN ('deleting','deleted')
            """, objectId);
        if (active != 1) throw new KmaException(409, "存储对象已进入删除流程，无法绑定文档");
        jdbc.update("UPDATE knowledge_doc SET storage_object_id=? WHERE doc_id=?", objectId, docId);
        jdbc.update("""
            INSERT INTO knowledge_storage_reference(object_id,doc_id,reference_type)
            VALUES (?,?,'source') ON CONFLICT DO NOTHING
            """, objectId, docId);
    }

    public void markOrphanIfUnreferenced(Long objectId) {
        if (objectId == null) return;
        jdbc.update("""
            UPDATE knowledge_storage_object o SET status='orphan',
                delete_after=COALESCE(delete_after,now()+(? * interval '1 hour')),update_time=now()
            WHERE o.object_id=?
              AND NOT EXISTS(SELECT 1 FROM knowledge_storage_reference r WHERE r.object_id=o.object_id)
            """, properties.getStorage().getOrphanGraceHours(), objectId);
    }

    public Map<String, Object> reconcileNow() {
        return reconcile();
    }

    public Map<String, Object> reconcile() {
        int limit = bounded(properties.getStorage().getReconciliationBatchSize(), 1, 10_000);
        List<Map<String, Object>> registered = jdbc.queryForList("""
            SELECT o.object_id,o.location,o.checksum_algorithm,o.checksum,o.status,
                   EXISTS(SELECT 1 FROM knowledge_storage_reference r WHERE r.object_id=o.object_id) referenced
            FROM knowledge_storage_object o
            WHERE o.status<>'deleted' ORDER BY o.object_id LIMIT ?
            """, limit);
        Set<String> known = new HashSet<>();
        int active = 0, orphan = 0, missing = 0, corrupt = 0, discovered = 0;
        for (Map<String, Object> row : registered) {
            Long id = ((Number) row.get("object_id")).longValue();
            String location = String.valueOf(row.get("location"));
            known.add(location);
            boolean referenced = Boolean.TRUE.equals(row.get("referenced"));
            try {
                StorageObjectMetadata metadata = storage.inspect(location);
                String expected = (String) row.get("checksum");
                String actual = metadata.checksum();
                if (expected != null && "SHA-256".equalsIgnoreCase((String) row.get("checksum_algorithm"))
                    && !"SHA-256".equalsIgnoreCase(metadata.checksumAlgorithm())) {
                    actual = sha256(location);
                }
                if (expected != null && actual != null && !expected.equalsIgnoreCase(actual)) {
                    updateState(id, "corrupt", "对象校验和不一致", null);
                    corrupt++;
                } else if (referenced) {
                    jdbc.update("""
                        UPDATE knowledge_storage_object SET status='active',size_bytes=?,last_reconciled_at=now(),
                            delete_after=NULL,error_message=NULL,update_time=now() WHERE object_id=?
                        """, metadata.sizeBytes(), id);
                    active++;
                } else {
                    updateState(id, "orphan", null, properties.getStorage().getOrphanGraceHours());
                    orphan++;
                }
            } catch (Exception ex) {
                updateState(id, "missing", truncate(ex.getMessage(), 500), null);
                missing++;
            }
        }
        try {
            for (StorageObjectMetadata physical : storage.list(limit)) {
                if (known.contains(physical.location())) continue;
                jdbc.update("""
                    INSERT INTO knowledge_storage_object
                        (location,storage_type,checksum_algorithm,checksum,size_bytes,status,
                         last_reconciled_at,delete_after,error_message)
                    VALUES (?,?,?,?,?,'orphan',now(),now()+(? * interval '1 hour'),'物理对象无数据库台账')
                    ON CONFLICT (location) DO NOTHING
                    """, physical.location(), properties.getStorage().getType(),
                    physical.checksumAlgorithm() == null ? "UNKNOWN" : physical.checksumAlgorithm(),
                    physical.checksum(), physical.sizeBytes(), properties.getStorage().getOrphanGraceHours());
                discovered++;
            }
        } catch (Exception ex) {
            log.warn("列举物理对象失败", ex);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("active", active);
        result.put("orphan", orphan);
        result.put("missing", missing);
        result.put("corrupt", corrupt);
        result.put("discovered", discovered);
        auditService.record("storage_reconciliation", missing + corrupt > 0 ? "warning" : "info", "completed",
            "storage", null, List.of(), result);
        return result;
    }

    @Scheduled(cron = "${knowledge.storage.reconcile-cron:0 30 2 * * ?}")
    public void scheduledReconcile() {
        if (properties.getStorage().isLifecycleEnabled()) reconcile();
    }

    @Scheduled(fixedDelayString = "${knowledge.storage.cleanup-fixed-delay:3600000}")
    public void cleanupDue() {
        if (properties.getStorage().isLifecycleEnabled()) cleanup();
    }

    public void cleanupNow() {
        cleanup();
    }

    private void cleanup() {
        int limit = bounded(properties.getStorage().getCleanupBatchSize(), 1, 1000);
        List<Map<String, Object>> candidates = jdbc.queryForList("""
            SELECT object_id,location FROM knowledge_storage_object o
            WHERE status IN ('orphan','delete_failed') AND delete_after<=now()
              AND NOT EXISTS(SELECT 1 FROM knowledge_storage_reference r WHERE r.object_id=o.object_id)
            ORDER BY delete_after LIMIT ?
            """, limit);
        for (Map<String, Object> candidate : candidates) {
            long id = ((Number) candidate.get("object_id")).longValue();
            String location = String.valueOf(candidate.get("location"));
            int claimed = jdbc.update("""
                UPDATE knowledge_storage_object SET status='deleting',update_time=now()
                WHERE object_id=? AND status IN ('orphan','delete_failed')
                  AND NOT EXISTS(SELECT 1 FROM knowledge_storage_reference r WHERE r.object_id=?)
                """, id, id);
            if (claimed != 1) continue;
            try {
                storage.delete(location);
                jdbc.update("""
                    UPDATE knowledge_storage_object SET status='deleted',deleted_at=now(),error_message=NULL,
                        update_time=now() WHERE object_id=?
                    """, id);
            } catch (Exception ex) {
                jdbc.update("""
                    UPDATE knowledge_storage_object SET status='delete_failed',error_message=?,
                        delete_after=now()+interval '1 hour',update_time=now() WHERE object_id=?
                    """, truncate(ex.getMessage(), 500), id);
            }
        }
    }

    public List<Map<String, Object>> list(String status, int limit) {
        int bounded = bounded(limit, 1, 500);
        String normalized = status == null ? "" : status.trim();
        return jdbc.queryForList("""
            SELECT o.*,COALESCE(r.reference_count,0) reference_count
            FROM knowledge_storage_object o LEFT JOIN (
                SELECT object_id,count(*) reference_count FROM knowledge_storage_reference GROUP BY object_id
            ) r ON r.object_id=o.object_id
            WHERE (?='' OR o.status=?) ORDER BY o.update_time DESC LIMIT ?
            """, normalized, normalized, bounded);
    }

    public PageResult<Map<String, Object>> page(String status, String keyword, int pageNum, int pageSize,
                                                String sortBy, String sortOrder) {
        String normalizedStatus = status == null ? "" : status.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String pattern = "%" + normalizedKeyword + "%";
        String orderColumn = switch (sortBy) {
            case "status" -> "o.status";
            case "sizeBytes" -> "o.size_bytes";
            case "location" -> "o.location";
            default -> "o.update_time";
        };
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM knowledge_storage_object o
            WHERE (?='' OR o.status=?)
              AND (?='' OR o.location ILIKE ? OR COALESCE(o.error_message,'') ILIKE ?)
            """, Long.class, normalizedStatus, normalizedStatus, normalizedKeyword, pattern, pattern);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT o.*,COALESCE(r.reference_count,0) reference_count
            FROM knowledge_storage_object o LEFT JOIN (
                SELECT object_id,count(*) reference_count FROM knowledge_storage_reference GROUP BY object_id
            ) r ON r.object_id=o.object_id
            WHERE (?='' OR o.status=?)
              AND (?='' OR o.location ILIKE ? OR COALESCE(o.error_message,'') ILIKE ?)
            ORDER BY %s %s, o.object_id DESC LIMIT ? OFFSET ?
            """.formatted(orderColumn, direction), normalizedStatus, normalizedStatus,
            normalizedKeyword, pattern, pattern, pageSize, (pageNum - 1) * pageSize);
        return new PageResult<>(rows, total == null ? 0 : total, pageNum, pageSize);
    }

    private String sha256(String location) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = storage.open(location)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void updateState(Long id, String status, String error, Integer graceHours) {
        if (graceHours == null) {
            jdbc.update("""
                UPDATE knowledge_storage_object SET status=?,error_message=?,last_reconciled_at=now(),
                    update_time=now() WHERE object_id=?
                """, status, error, id);
        } else {
            jdbc.update("""
                UPDATE knowledge_storage_object SET status=?,error_message=?,last_reconciled_at=now(),
                    delete_after=COALESCE(delete_after,now()+(? * interval '1 hour')),update_time=now()
                WHERE object_id=?
                """, status, error, graceHours, id);
        }
    }

    private int bounded(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.substring(0, Math.min(max, value.length()));
    }
}
