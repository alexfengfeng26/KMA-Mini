package com.kma.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.result.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class SecurityAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SecurityAuditService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc; this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String severity, String action, String resource,
                       String contentHash, List<String> flags, Map<String, Object> details) {
        recordBestEffortInternal(eventType, severity, action, resource, contentHash, flags, details);
    }

    /** 观察性审计：存储异常只降级，不得影响问答、限流等主链路。 */
    @Transactional(transactionManager = "knowledgeTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void recordBestEffort(String eventType, String severity, String action, String resource,
                                 String contentHash, List<String> flags, Map<String, Object> details) {
        recordBestEffortInternal(eventType, severity, action, resource, contentHash, flags, details);
    }

    /** 权限审计：必须加入调用方事务，写入失败时回滚权限变更。 */
    @Transactional(transactionManager = "knowledgeTransactionManager", propagation = Propagation.MANDATORY)
    public void recordRequired(String eventType, String severity, String action, String resource,
                               Map<String, Object> before, Map<String, Object> after,
                               Map<String, Object> details) {
        try {
            insert(eventType, severity, action, resource, null, List.of(), details, before, after);
        } catch (Exception ex) {
            log.error("required authorization audit failed: action={}, resource={}", action, resource, ex);
            throw new KmaException(409, "AUTHORIZATION_AUDIT_REQUIRED");
        }
    }

    private void recordBestEffortInternal(String eventType, String severity, String action, String resource,
                                          String contentHash, List<String> flags, Map<String, Object> details) {
        try {
            insert(eventType, severity, action, resource, contentHash, flags, details, null, null);
        } catch (Exception ex) {
            log.error("best-effort security audit failed: action={}, resource={}", action, resource, ex);
        }
    }

    private void insert(String eventType, String severity, String action, String resource,
                        String contentHash, List<String> flags, Map<String, Object> details,
                        Map<String, Object> before, Map<String, Object> after) throws Exception {
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        String[] target = target(resource);
        jdbc.update("""
            INSERT INTO kma_security_audit(subject_id,username,event_type,severity,action,
                resource,content_hash,flags,details,trace_id,actor_token_source,target_type,target_id,
                before_state,after_state)
            VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?::jsonb,?::jsonb)
            """, principal == null ? null : principal.getSubjectId(),
            principal == null ? null : principal.getUsername(), eventType, severity, action, resource,
            contentHash, objectMapper.writeValueAsString(flags == null ? List.of() : flags),
            objectMapper.writeValueAsString(sanitize(details)), MDC.get("traceId"),
            principal == null ? null : principal.getTokenSource(), target[0], target[1],
            before == null ? null : objectMapper.writeValueAsString(sanitize(before)),
            after == null ? null : objectMapper.writeValueAsString(sanitize(after)));
    }

    public List<Map<String, Object>> list(int limit) {
        return jdbc.queryForList("""
            SELECT audit_id,subject_id,username,event_type,severity,action,resource,content_hash,flags,details,
                   trace_id,actor_token_source,target_type,target_id,before_state,after_state,create_time
            FROM kma_security_audit ORDER BY create_time DESC LIMIT ?
            """, Math.min(500, Math.max(1, limit)));
    }

    public PageResult<Map<String, Object>> page(int pageNum, int pageSize, String keyword,
                                                String sortBy, String sortOrder) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String pattern = "%" + normalizedKeyword + "%";
        String orderColumn = switch (sortBy) {
            case "severity" -> "severity";
            case "eventType" -> "event_type";
            case "username" -> "username";
            default -> "create_time";
        };
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM kma_security_audit
            WHERE (?='' OR COALESCE(username,'') ILIKE ?
                OR event_type ILIKE ? OR action ILIKE ? OR COALESCE(resource,'') ILIKE ?)
            """, Long.class, normalizedKeyword, pattern, pattern, pattern, pattern);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT audit_id,subject_id,username,event_type,severity,action,resource,content_hash,flags,details,
                   trace_id,actor_token_source,target_type,target_id,before_state,after_state,create_time
            FROM kma_security_audit
            WHERE (?='' OR COALESCE(username,'') ILIKE ?
                OR event_type ILIKE ? OR action ILIKE ? OR COALESCE(resource,'') ILIKE ?)
            ORDER BY %s %s, audit_id DESC LIMIT ? OFFSET ?
            """.formatted(orderColumn, direction), normalizedKeyword, pattern, pattern, pattern, pattern,
            pageSize, (pageNum - 1) * pageSize);
        return new PageResult<>(rows, total == null ? 0 : total, pageNum, pageSize);
    }

    private String[] target(String resource) {
        if (resource == null || resource.isBlank()) return new String[] {null, null};
        int separator = resource.indexOf(':');
        return separator < 0 ? new String[] {resource, null}
            : new String[] {resource.substring(0, separator), resource.substring(separator + 1)};
    }

    private Map<String, Object> sanitize(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, sensitive(key) ? "***" : sanitizeValue(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, nested) -> converted.put(String.valueOf(key), nested));
            return sanitize(converted);
        }
        if (value instanceof Iterable<?> values) {
            java.util.ArrayList<Object> result = new java.util.ArrayList<>();
            values.forEach(item -> result.add(sanitizeValue(item)));
            return result;
        }
        return value;
    }

    private boolean sensitive(String key) {
        String normalized = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("password") || normalized.contains("secret")
            || normalized.contains("token") || normalized.contains("credential")
            || normalized.contains("apikey") || normalized.contains("api_key");
    }
}
