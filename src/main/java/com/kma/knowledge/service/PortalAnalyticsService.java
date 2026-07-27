package com.kma.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.knowledge.dto.PortalEventRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalAnalyticsService {
    private final JdbcTemplate knowledgeJdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(transactionManager = "knowledgeTransactionManager")
    public void record(String siteKey, PortalEventRequest request) {
        Long siteId = siteId(siteKey);
        knowledgeJdbcTemplate.update("""
            INSERT INTO knowledge_portal_event
              (site_id,user_id,event_type,page_slug,query_text,target_id,metadata)
            VALUES (?,?,?,?,?,?,?::jsonb)
            """, siteId, KmaIdentityContext.getUserId(), request.getEventType(),
            request.getPageSlug(), request.getQueryText(), request.getTargetId(), json(request.getMetadata()));
    }

    public Map<String, Object> summary(String siteKey, int days) {
        Long siteId = siteId(siteKey);
        int bounded = Math.max(1, Math.min(days, 90));
        List<Map<String, Object>> totals = knowledgeJdbcTemplate.queryForList("""
            SELECT event_type AS "eventType",count(*) AS total
            FROM knowledge_portal_event
            WHERE site_id=? AND create_time>=now()-(? || ' days')::interval
            GROUP BY event_type ORDER BY total DESC
            """, siteId, bounded);
        List<Map<String, Object>> searches = knowledgeJdbcTemplate.queryForList("""
            SELECT query_text AS keyword,count(*) AS total
            FROM knowledge_portal_event
            WHERE site_id=? AND event_type IN ('search','search_empty')
              AND query_text IS NOT NULL AND create_time>=now()-(? || ' days')::interval
            GROUP BY query_text ORDER BY total DESC LIMIT 20
            """, siteId, bounded);
        return Map.of("days", bounded, "totals", totals, "topSearches", searches);
    }

    private Long siteId(String siteKey) {
        List<Long> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT site_id FROM knowledge_portal_site WHERE site_key=?
            """, Long.class, siteKey);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_SITE_NOT_FOUND");
        return rows.getFirst();
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new KmaException(400, "PORTAL_EVENT_METADATA_INVALID");
        }
    }

}
