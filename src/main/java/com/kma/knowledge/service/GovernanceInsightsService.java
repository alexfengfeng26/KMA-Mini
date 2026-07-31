package com.kma.knowledge.service;

import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.GovernancePolicyRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GovernanceInsightsService {
    private final JdbcTemplate jdbc;
    private final SecurityAuditService audit;

    public GovernanceInsightsService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc, SecurityAuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public Map<String, Object> policy() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT content_separation_of_duties AS "contentSeparationOfDuties",update_time AS "updateTime"
            FROM kma_governance_policy WHERE policy_key='default'
            """);
        return rows.isEmpty() ? Map.of("contentSeparationOfDuties", false) : rows.getFirst();
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> updatePolicy(GovernancePolicyRequest request) {
        Map<String, Object> before = policy();
        jdbc.update("""
            UPDATE kma_governance_policy SET content_separation_of_duties=?,updated_by=?,update_time=now()
            WHERE policy_key='default'
            """, request.isContentSeparationOfDuties(), KmaIdentityContext.getUserId());
        Map<String, Object> after = policy();
        audit.recordRequired("governance_policy", "warning", "governance.policy.update", "governance:default",
            before, after, Map.of());
        return after;
    }

    public Map<String, Object> insights() {
        return Map.of(
            "scheduledOnline", count("""
                SELECT count(*) FROM knowledge_doc WHERE publication_managed AND workflow_status='published'
                AND online=FALSE AND scheduled_online_at>now()
                """),
            "scheduledOffline", count("""
                SELECT count(*) FROM knowledge_doc WHERE publication_managed AND workflow_status='published'
                AND online=TRUE AND scheduled_offline_at>now()
                """),
            "expiringSoon", count("""
                SELECT count(*) FROM knowledge_doc WHERE publication_managed AND online=TRUE
                AND expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE+30
                """),
            "parsePending", count("""
                SELECT count(*) FROM knowledge_doc WHERE publication_managed
                AND parse_status IN ('pending','processing','failed')
                """),
            "withoutTopics", count("""
                SELECT count(*) FROM knowledge_doc d WHERE publication_managed AND NOT EXISTS
                (SELECT 1 FROM knowledge_doc_topic dt WHERE dt.doc_id=d.doc_id)
                """),
            "duplicateReferences", count("""
                SELECT count(*) FROM (SELECT space_id,document_number,issuing_authority
                FROM knowledge_doc WHERE publication_managed AND document_number IS NOT NULL
                GROUP BY space_id,document_number,issuing_authority HAVING count(*)>1) x
                """),
            "unhelpfulAnswers", count("SELECT count(*) FROM knowledge_qa_feedback WHERE rating='unhelpful' AND created_at>=now()-interval '30 days'"),
            "searchWithoutResult", count("SELECT count(*) FROM knowledge_portal_event WHERE event_type='search_empty' AND create_time>=now()-interval '30 days'"),
            "reviewing", count("""
                SELECT count(*) FROM knowledge_doc WHERE publication_managed AND workflow_status='reviewing'
                AND (review_decision IS NULL OR review_decision != 'approved')
                """),
            "pendingPublish", count("""
                SELECT count(*) FROM knowledge_doc WHERE publication_managed AND review_decision='approved'
                AND workflow_status != 'published'
                """));
    }

    public Map<String, Object> contentImpact(Long contentId) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM knowledge_doc WHERE doc_id=? AND publication_managed", Integer.class, contentId);
        if (exists == null || exists == 0) throw new KmaException(404, "CONTENT_NOT_FOUND");
        return Map.of(
            "topicCount", count("SELECT count(*) FROM knowledge_doc_topic WHERE doc_id=" + contentId),
            "favorites", count("SELECT count(*) FROM knowledge_favorite WHERE doc_id=" + contentId),
            "readers", count("SELECT count(*) FROM knowledge_read_history WHERE doc_id=" + contentId),
            "citations", count("SELECT count(*) FROM knowledge_chunk WHERE doc_id=" + contentId));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
