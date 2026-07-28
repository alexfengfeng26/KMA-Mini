package com.kma.knowledge.service;

import com.kma.common.security.SecurityAuditService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Executes already-approved publication windows without changing review decisions. */
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ContentPublicationScheduleService {
    private final JdbcTemplate jdbc;
    private final SecurityAuditService audit;

    public ContentPublicationScheduleService(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                                             SecurityAuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${knowledge.content.schedule-fixed-delay:30000}")
    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void tick() {
        processDueWindows();
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void processDueWindows() {
        List<Long> online = jdbc.queryForList("""
            UPDATE knowledge_doc SET online=TRUE,is_active=TRUE,activated_at=now(),update_time=now()
            WHERE publication_managed=TRUE AND workflow_status='published' AND online=FALSE
              AND scheduled_online_at IS NOT NULL AND scheduled_online_at<=now()
              AND (scheduled_offline_at IS NULL OR scheduled_offline_at>now())
            RETURNING doc_id
            """, Long.class);
        for (Long docId : online) audit.recordRequired("content_workflow", "warning", "content.schedule.activated",
            "content:" + docId, Map.of(), Map.of("online", true, "active", true), Map.of("scheduler", true));

        List<Long> offline = jdbc.queryForList("""
            UPDATE knowledge_doc SET online=FALSE,is_active=FALSE,update_time=now()
            WHERE publication_managed=TRUE AND workflow_status='published' AND online=TRUE
              AND scheduled_offline_at IS NOT NULL AND scheduled_offline_at<=now()
            RETURNING doc_id
            """, Long.class);
        for (Long docId : offline) audit.recordRequired("content_workflow", "warning", "content.schedule.offlined",
            "content:" + docId, Map.of(), Map.of("online", false, "active", false), Map.of("scheduler", true));
    }
}
