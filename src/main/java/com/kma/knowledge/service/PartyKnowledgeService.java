package com.kma.knowledge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.*;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Business service for the internal party-knowledge portal and its publication workflow. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PartyKnowledgeService {
    private static final Set<String> HISTORICAL = Set.of("expired", "repealed");

    private final JdbcTemplate knowledgeJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeSpaceAclService aclService;
    private final SecurityAuditService auditService;

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public PartyContentView createText(PartyContentRequest request) {
        DocIngestTextRequest ingest = new DocIngestTextRequest();
        ingest.setSpaceCode(request.getSpaceCode());
        ingest.setTitle(request.getTitle());
        ingest.setSourceTag(request.getSourceTag());
        ingest.setExternalRef(externalRef(request.getExternalRef()));
        ingest.setSourceVersion(request.getSourceVersion());
        ingest.setContent(request.getContent());
        DocIngestResult created = ingestionService.ingestText(ingest);
        markGoverned(created.getDocId(), request.getTitle(), request.getContentType(), request.getDocumentNumber(),
            request.getIssuingAuthority(), request.getPublishDate(), request.getEffectiveDate(), request.getExpiryDate(),
            request.getValidityStatus(), request.getSummary(), request.getKeywords(), request.getTopicCodes());
        applySchedule(created.getDocId(), request.getScheduledOnlineAt(), request.getScheduledOfflineAt(), request.getScheduleNote());
        auditService.recordRequired("content_change", "info", "content.create", "content:" + created.getDocId(),
            Map.of(), Map.of("workflowStatus", "draft", "title", request.getTitle()), Map.of("source", "text"));
        return getAdminContent(created.getDocId());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public PartyContentView createFile(PartyContentFileRequest request, MultipartFile file) {
        DocIngestFileRequest ingest = new DocIngestFileRequest();
        ingest.setSpaceCode(request.getSpaceCode());
        ingest.setSourceTag(request.getSourceTag());
        ingest.setExternalRef(externalRef(request.getExternalRef()));
        ingest.setSourceVersion(request.getSourceVersion());
        DocIngestResult created = ingestionService.ingestFile(ingest, file);
        markGoverned(created.getDocId(), request.getTitle(), request.getContentType(), request.getDocumentNumber(),
            request.getIssuingAuthority(), request.getPublishDate(), request.getEffectiveDate(), request.getExpiryDate(),
            request.getValidityStatus(), request.getSummary(), request.getKeywords(), request.getTopicCodes());
        applySchedule(created.getDocId(), request.getScheduledOnlineAt(), request.getScheduledOfflineAt(), request.getScheduleNote());
        auditService.recordRequired("content_change", "info", "content.create", "content:" + created.getDocId(),
            Map.of(), Map.of("workflowStatus", "draft", "title", request.getTitle()), Map.of("source", "file"));
        return getAdminContent(created.getDocId());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public PartyContentView update(Long id, PartyContentMetadataRequest request) {
        KnowledgeDoc doc = requireManaged(id);
        KnowledgeSpace space = requireSpace(doc);
        aclService.assertIngestAccess(space.getSpaceCode());
        if (!"draft".equals(doc.getWorkflowStatus())) throw new KmaException(409, "CONTENT_NOT_EDITABLE");
        Map<String, Object> before = snapshot(doc);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_doc SET title=COALESCE(?,title),content_type=COALESCE(?,content_type),
              document_number=?,issuing_authority=?,publish_date=COALESCE(?,publish_date),effective_date=?,expiry_date=?,
              validity_status=COALESCE(?,validity_status),summary=?,keywords=?::jsonb,review_decision=NULL,
              review_note=NULL,update_time=now() WHERE doc_id=?
            """, request.getTitle(), request.getContentType(), request.getDocumentNumber(), request.getIssuingAuthority(),
            request.getPublishDate(), request.getEffectiveDate(), request.getExpiryDate(), request.getValidityStatus(),
            request.getSummary(), json(request.getKeywords() == null ? keywords(doc.getKeywords()) : request.getKeywords()),
            id);
        if (request.getScheduledOnlineAt() != null || request.getScheduledOfflineAt() != null || request.getScheduleNote() != null) {
            applySchedule(id, request.getScheduledOnlineAt(), request.getScheduledOfflineAt(), request.getScheduleNote());
        }
        if (request.getTopicCodes() != null) assignTopics(id, request.getTopicCodes());
        PartyContentView updated = getAdminContent(id);
        auditService.recordRequired("content_change", "info", "content.update", "content:" + id,
            before, viewSnapshot(updated), Map.of());
        return updated;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void submit(Long id) {
        KnowledgeDoc doc = lockManaged(id);
        aclService.assertIngestAccess(requireSpace(doc).getSpaceCode());
        if (!"draft".equals(doc.getWorkflowStatus())) throw new KmaException(409, "CONTENT_NOT_DRAFT");
        if (!"completed".equals(doc.getParseStatus())) throw new KmaException(409, "CONTENT_PARSE_NOT_COMPLETED");
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_doc SET workflow_status='reviewing',review_decision='pending',review_note=NULL,
              submitted_at=now(),reviewer_id=NULL,reviewed_at=NULL,update_time=now()
            WHERE doc_id=?
            """, id);
        auditService.recordRequired("content_workflow", "info", "content.submit", "content:" + id,
            snapshot(doc), Map.of("workflowStatus", "reviewing", "reviewDecision", "pending"), Map.of());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void approve(Long id, ReviewRequest request) {
        KnowledgeDoc doc = lockManaged(id);
        aclService.assertAdminAccess(requireSpace(doc).getSpaceCode());
        assertSeparationOfDuties(doc, "review");
        if (!"reviewing".equals(doc.getWorkflowStatus())) throw new KmaException(409, "CONTENT_NOT_REVIEWING");
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_doc SET review_decision='approved',review_note=?,reviewer_id=?,reviewed_at=now(),update_time=now()
            WHERE doc_id=?
            """, request == null ? null : request.getNote(), KmaIdentityContext.getUserId(), id);
        auditService.recordRequired("content_workflow", "info", "content.approve", "content:" + id,
            snapshot(doc), Map.of("reviewDecision", "approved"), Map.of());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void reject(Long id, ReviewRequest request) {
        KnowledgeDoc doc = lockManaged(id);
        aclService.assertAdminAccess(requireSpace(doc).getSpaceCode());
        if (!"reviewing".equals(doc.getWorkflowStatus())) throw new KmaException(409, "CONTENT_NOT_REVIEWING");
        String note = request == null ? null : request.getNote();
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_doc SET workflow_status='draft',review_decision='rejected',review_note=?,reviewer_id=?,
              reviewed_at=now(),online=FALSE,is_active=FALSE,update_time=now() WHERE doc_id=?
            """, note, KmaIdentityContext.getUserId(), id);
        auditService.recordRequired("content_workflow", "warning", "content.reject", "content:" + id,
            snapshot(doc), Map.of("workflowStatus", "draft", "reviewDecision", "rejected"),
            note == null ? Map.of() : Map.of("note", note));
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void publish(Long id) {
        KnowledgeDoc doc = lockManaged(id);
        aclService.assertAdminAccess(requireSpace(doc).getSpaceCode());
        assertSeparationOfDuties(doc, "publish");
        boolean restore = "published".equals(doc.getWorkflowStatus()) && !Boolean.TRUE.equals(doc.getOnline());
        if (!restore && (!"reviewing".equals(doc.getWorkflowStatus()) || !"approved".equals(doc.getReviewDecision()))) {
            throw new KmaException(409, "CONTENT_REVIEW_APPROVAL_REQUIRED");
        }
        if (!"completed".equals(doc.getParseStatus())) throw new KmaException(409, "CONTENT_PARSE_NOT_COMPLETED");
        if (StringUtils.hasText(doc.getExternalRef())) {
            knowledgeJdbcTemplate.update("""
                UPDATE knowledge_doc SET is_active=FALSE,update_time=now()
                WHERE space_id=? AND external_ref=? AND doc_id<>? AND is_active=TRUE
                """, doc.getSpaceId(), doc.getExternalRef(), id);
        }
        boolean scheduled = doc.getScheduledOnlineAt() != null && doc.getScheduledOnlineAt().isAfter(LocalDateTime.now());
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_doc SET workflow_status='published',online=?,is_active=?,published_at=now(),
              activated_at=CASE WHEN ? THEN NULL ELSE now() END,update_time=now() WHERE doc_id=?
            """, !scheduled, !scheduled, scheduled, id);
        auditService.recordRequired("content_workflow", "warning", scheduled ? "content.schedule.online" : (restore ? "content.restore" : "content.publish"),
            "content:" + id, snapshot(doc), Map.of("workflowStatus", "published", "online", !scheduled, "active", !scheduled),
            scheduled ? Map.of("scheduledOnlineAt", doc.getScheduledOnlineAt().toString()) : Map.of());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void offline(Long id, ReviewRequest request) {
        KnowledgeDoc doc = lockManaged(id);
        aclService.assertAdminAccess(requireSpace(doc).getSpaceCode());
        if (!"published".equals(doc.getWorkflowStatus()) || !Boolean.TRUE.equals(doc.getOnline())) {
            throw new KmaException(409, "CONTENT_NOT_ONLINE");
        }
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_doc SET online=FALSE,is_active=FALSE,review_note=?,update_time=now()
            WHERE doc_id=?
            """, request == null ? null : request.getNote(), id);
        auditService.recordRequired("content_workflow", "warning", "content.offline", "content:" + id,
            snapshot(doc), Map.of("workflowStatus", "published", "online", false, "active", false), Map.of());
    }

    public Map<String, Object> adminPage(AdminContentQuery request) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE d.publication_managed=TRUE");
        readableSpaces(where, args, "d");
        if (StringUtils.hasText(request.getKeyword())) {
            where.append(" AND (d.title ILIKE ? OR d.document_number ILIKE ? OR d.issuing_authority ILIKE ?)");
            String term = "%" + request.getKeyword().trim() + "%"; args.add(term); args.add(term); args.add(term);
        }
        eq(where,args,"d.content_type",request.getContentType());
        eq(where,args,"d.workflow_status",request.getWorkflowStatus());
        eq(where,args,"d.review_decision",request.getReviewDecision());
        eq(where,args,"s.space_code",request.getSpaceCode());
        long total = knowledgeJdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_doc d JOIN knowledge_space s ON s.space_id=d.space_id" + where,
            Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(request.getPageSize()); pageArgs.add((request.getPageNum()-1) * request.getPageSize());
        List<PartyContentView> list = knowledgeJdbcTemplate.query(
            selectColumns() + " FROM knowledge_doc d JOIN knowledge_space s ON s.space_id=d.space_id"
                + where + " ORDER BY d.update_time DESC LIMIT ? OFFSET ?",
            (rs,row) -> mapContent(rs), pageArgs.toArray());
        enrich(list, false);
        return page(list,total,request.getPageNum(),request.getPageSize());
    }

    public Map<String, Object> portalPage(PortalContentQuery request) {
        return portalPage(request, PortalContentScope.unrestricted());
    }

    public Map<String, Object> portalPage(PortalContentQuery request, PortalContentScope siteScope) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE " + portalVisibility("d"));
        readableSpaces(where,args,"d");
        applySiteScope(where,args,siteScope,"d","s");
        if (StringUtils.hasText(request.getKeyword())) {
            where.append(" AND (d.title ILIKE ? OR d.document_number ILIKE ? OR d.summary ILIKE ? OR EXISTS (SELECT 1 FROM knowledge_chunk c WHERE c.doc_id=d.doc_id AND c.content ILIKE ?))");
            String term = "%" + request.getKeyword().trim() + "%";
            args.add(term); args.add(term); args.add(term); args.add(term);
        }
        eq(where,args,"d.content_type",request.getContentType());
        eq(where,args,"d.issuing_authority",request.getIssuingAuthority());
        eq(where,args,"s.space_code",request.getSpaceCode());
        if (StringUtils.hasText(request.getValidityStatus())) {
            eq(where,args,"d.validity_status",request.getValidityStatus());
        } else if (!Boolean.TRUE.equals(request.getIncludeHistorical())) {
            where.append(" AND d.validity_status IN ('effective','pending')");
        }
        if (request.getPublishDateFrom()!=null) { where.append(" AND d.publish_date>=?"); args.add(request.getPublishDateFrom()); }
        if (request.getPublishDateTo()!=null) { where.append(" AND d.publish_date<=?"); args.add(request.getPublishDateTo()); }
        if (StringUtils.hasText(request.getTopicCode())) {
            where.append(" AND EXISTS (SELECT 1 FROM knowledge_doc_topic dt JOIN knowledge_topic t ON t.topic_id=dt.topic_id WHERE dt.doc_id=d.doc_id AND t.topic_code=?)");
            args.add(request.getTopicCode());
        }
        long total = knowledgeJdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_doc d JOIN knowledge_space s ON s.space_id=d.space_id" + where,
            Long.class,args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(request.getPageSize()); pageArgs.add((request.getPageNum()-1)*request.getPageSize());
        List<PartyContentView> list = knowledgeJdbcTemplate.query(
            selectColumns()+" FROM knowledge_doc d JOIN knowledge_space s ON s.space_id=d.space_id"
                +where+" ORDER BY d.publish_date DESC NULLS LAST,d.published_at DESC LIMIT ? OFFSET ?",
            (rs,row)->mapContent(rs),pageArgs.toArray());
        enrich(list,true);
        return page(list,total,request.getPageNum(),request.getPageSize());
    }

    public PartyContentView getPortalContent(Long id, String location) {
        return getPortalContent(id, location, PortalContentScope.unrestricted());
    }

    public PartyContentView getPortalContent(Long id, String location, PortalContentScope siteScope) {
        PartyContentView view = queryOne(id, true);
        aclService.assertReadAccess(view.getSpaceCode());
        assertInSiteScope(id,siteScope);
        view.setSections(knowledgeJdbcTemplate.queryForList("""
            SELECT chunk_id,chunk_index,content,char_offset,meta FROM knowledge_chunk
            WHERE doc_id=? ORDER BY chunk_index
            """, id));
        view.setVersions(versionRows(view));
        view.setRelated(relatedRows(view));
        recordHistory(id, location);
        return view;
    }

    public PartyContentView getAdminContent(Long id) {
        PartyContentView view = queryOne(id, false);
        aclService.assertReadAccess(view.getSpaceCode());
        view.setSections(knowledgeJdbcTemplate.queryForList("""
            SELECT chunk_id,chunk_index,content,char_offset,meta FROM knowledge_chunk
            WHERE doc_id=? ORDER BY chunk_index
            """, id));
        view.setVersions(versionRows(view));
        return view;
    }

    public KnowledgeDoc getPortalDocument(Long id) {
        PartyContentView view = queryOne(id, true);
        aclService.assertReadAccess(view.getSpaceCode());
        return docMapper.selectById(id);
    }

    public Map<String, Object> home() {
        return home(PortalContentScope.unrestricted());
    }

    public Map<String, Object> home(PortalContentScope siteScope) {
        PortalContentQuery recentQuery = new PortalContentQuery(); recentQuery.setPageSize(8);
        Map<String,Object> config = config();
        List<Map<String,Object>> topicRows=topics(true).stream()
            .filter(row->siteScope.topicCodes().isEmpty()
                ||siteScope.topicCodes().contains(String.valueOf(row.get("topic_code"))))
            .limit(6).toList();
        boolean unrestricted=siteScope.allSpaces()&&siteScope.contentTypes().isEmpty()
            &&siteScope.topicCodes().isEmpty();
        return Map.of("config",config,"categories",scopedCategoryCards(siteScope),
            "recent",portalPage(recentQuery,siteScope).get("list"),"topics",topicRows,
            "history",unrestricted?history(6):List.of(),"favorites",unrestricted?favorites(6):List.of());
    }

    public List<Map<String, Object>> topics(boolean portalOnly) {
        String sql = """
            SELECT t.topic_id, t.topic_code, t.name, t.description, t.cover_color, t.sort_order,
                   t.enabled, t.featured, t.system_topic, t.parent_topic_id, t.topic_type, t.icon,
                   t.slug, t.display_mode, COALESCE(c.total,0) AS content_count
            FROM knowledge_topic t
            LEFT JOIN (SELECT topic_id, count(*) AS total FROM knowledge_doc_topic GROUP BY topic_id) c
              ON c.topic_id = t.topic_id
            WHERE TRUE"""
            + (portalOnly ? " AND t.enabled=TRUE" : "") + " ORDER BY t.sort_order,t.topic_id";
        return knowledgeJdbcTemplate.queryForList(sql);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Long saveTopic(Long id, TopicRequest request) {
        validateTopicParent(id,request.getParentTopicId());
        String slug=StringUtils.hasText(request.getSlug())?request.getSlug():request.getTopicCode();
        if (id == null) {
            Long topicId = knowledgeJdbcTemplate.queryForObject("""
                INSERT INTO knowledge_topic(topic_code,name,description,cover_color,sort_order,enabled,featured,
                  parent_topic_id,topic_type,icon,slug,display_mode)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?) RETURNING topic_id
                """, Long.class,request.getTopicCode(),request.getName(),request.getDescription(),request.getCoverColor(),
                request.getSortOrder(),request.getEnabled(),request.getFeatured(),request.getParentTopicId(),
                request.getTopicType(),request.getIcon(),slug,request.getDisplayMode());
            auditService.recordRequired("content_configuration", "info", "topic.create", "topic:"+topicId,
                Map.of(),Map.of("code",request.getTopicCode(),"name",request.getName()),Map.of());
            return topicId;
        }
        Map<String,Object> before = knowledgeJdbcTemplate.queryForMap("SELECT topic_code,name,enabled,featured FROM knowledge_topic WHERE topic_id=?",id);
        int changed = knowledgeJdbcTemplate.update("""
            UPDATE knowledge_topic SET name=?,description=?,cover_color=?,sort_order=?,enabled=?,featured=?,update_time=now()
              ,parent_topic_id=?,topic_type=?,icon=?,slug=?,display_mode=?
            WHERE topic_id=?
            """,request.getName(),request.getDescription(),request.getCoverColor(),request.getSortOrder(),request.getEnabled(),request.getFeatured(),
            request.getParentTopicId(),request.getTopicType(),request.getIcon(),slug,request.getDisplayMode(),id);
        if (changed==0) throw new KmaException(404,"TOPIC_NOT_FOUND");
        auditService.recordRequired("content_configuration", "info", "topic.update", "topic:"+id,
            before,topicAuditSnapshot(request),Map.of());
        return id;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void deleteTopic(Long id) {
        Map<String,Object> row = knowledgeJdbcTemplate.queryForMap(
            "SELECT system_topic FROM knowledge_topic WHERE topic_id=?", id);
        if (Boolean.TRUE.equals(row.get("system_topic"))) {
            throw new KmaException(403, "SYSTEM_TOPIC_CANNOT_DELETE");
        }
        Integer contentCount = knowledgeJdbcTemplate.queryForObject(
            "SELECT count(*) FROM knowledge_doc_topic WHERE topic_id=?", Integer.class, id);
        if (contentCount != null && contentCount > 0) {
            throw new KmaException(409, "TOPIC_HAS_CONTENT");
        }
        Integer childCount = knowledgeJdbcTemplate.queryForObject(
            "SELECT count(*) FROM knowledge_topic WHERE parent_topic_id=?", Integer.class, id);
        if (childCount != null && childCount > 0) {
            throw new KmaException(409, "TOPIC_HAS_CHILDREN");
        }
        knowledgeJdbcTemplate.update("DELETE FROM knowledge_topic WHERE topic_id=?", id);
        auditService.recordRequired("content_configuration", "warning", "topic.delete", "topic:"+id,
            Map.of("topicId", id), Map.of(), Map.of());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void reorderTopics(List<Map<String, Object>> order) {
        for (Map<String, Object> item : order) {
            Number id = (Number) item.get("topicId");
            Number sortOrder = (Number) item.get("sortOrder");
            if (id == null || sortOrder == null) continue;
            knowledgeJdbcTemplate.update(
                "UPDATE knowledge_topic SET sort_order=?,update_time=now() WHERE topic_id=?",
                sortOrder.intValue(), id.longValue());
        }
    }

    private void validateTopicParent(Long topicId,Long parentTopicId){
        if(parentTopicId==null)return;
        if(Objects.equals(topicId,parentTopicId))throw new KmaException(409,"TOPIC_PARENT_CYCLE");
        Integer exists=knowledgeJdbcTemplate.queryForObject(
            "SELECT count(*) FROM knowledge_topic WHERE topic_id=?",
            Integer.class,parentTopicId);
        if(exists==null||exists==0)throw new KmaException(404,"TOPIC_PARENT_NOT_FOUND");
        if(topicId==null)return;
        Integer cycle=knowledgeJdbcTemplate.queryForObject("""
            WITH RECURSIVE descendants AS (
              SELECT topic_id FROM knowledge_topic WHERE parent_topic_id=?
              UNION ALL
              SELECT t.topic_id FROM knowledge_topic t JOIN descendants d ON t.parent_topic_id=d.topic_id
            )
            SELECT count(*) FROM descendants WHERE topic_id=?
            """,Integer.class,topicId,parentTopicId);
        if(cycle!=null&&cycle>0)throw new KmaException(409,"TOPIC_PARENT_CYCLE");
    }

    public Map<String,Object> config() {
        List<Map<String,Object>> rows=knowledgeJdbcTemplate.queryForList("SELECT unit_name,help_text,current_topic_code,update_time FROM knowledge_portal_config WHERE config_id=1");
        return rows.isEmpty()?Map.of("unit_name","KMA 党建知识库"):rows.getFirst();
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void updateConfig(PortalConfigRequest request) {
        Map<String,Object> before=config();
        knowledgeJdbcTemplate.update("""
            INSERT INTO knowledge_portal_config(config_id,unit_name,help_text,current_topic_code,update_time)
            VALUES (1,?,?,?,now()) ON CONFLICT (config_id) DO UPDATE SET unit_name=EXCLUDED.unit_name,
              help_text=EXCLUDED.help_text,current_topic_code=EXCLUDED.current_topic_code,update_time=now()
            """,request.getUnitName(),request.getHelpText(),request.getCurrentTopicCode());
        auditService.recordRequired("content_configuration","info","portal.configure","portal:configuration",before,
            Map.of("unitName",request.getUnitName()),Map.of());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Long addFavorite(FavoriteRequest request) {
        Long userId=requireUser();
        if ("content".equals(request.getFavoriteType())) getPortalContent(request.getDocId(),null);
        else {
            Integer count=knowledgeJdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_chat_session WHERE user_id=? AND session_id=?",Integer.class,userId,request.getSessionId());
            if (count==null||count==0) throw new KmaException(404,"SESSION_NOT_FOUND");
        }
        if ("content".equals(request.getFavoriteType())) {
            return knowledgeJdbcTemplate.queryForObject("""
                INSERT INTO knowledge_favorite(user_id,favorite_type,doc_id,title)
                VALUES (?, 'content',?,?)
                ON CONFLICT (user_id,doc_id) WHERE favorite_type='content'
                DO UPDATE SET title=COALESCE(EXCLUDED.title,knowledge_favorite.title) RETURNING favorite_id
                """,Long.class,userId,request.getDocId(),request.getTitle());
        }
        return knowledgeJdbcTemplate.queryForObject("""
            INSERT INTO knowledge_favorite(user_id,favorite_type,session_id,title)
            VALUES (?, 'qa',?,?)
            ON CONFLICT (user_id,session_id) WHERE favorite_type='qa'
            DO UPDATE SET title=COALESCE(EXCLUDED.title,knowledge_favorite.title) RETURNING favorite_id
            """,Long.class,userId,request.getSessionId(),request.getTitle());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void removeFavorite(Long id) {
        int changed=knowledgeJdbcTemplate.update("DELETE FROM knowledge_favorite WHERE user_id=? AND favorite_id=?",requireUser(),id);
        if(changed==0) throw new KmaException(404,"FAVORITE_NOT_FOUND");
    }

    public List<Map<String,Object>> favorites(int limit) {
        Long userId=KmaIdentityContext.getUserId(); if(userId==null)return List.of();
        return knowledgeJdbcTemplate.queryForList("""
            SELECT f.favorite_id,f.favorite_type,f.doc_id,f.session_id,COALESCE(f.title,d.title,s.title) title,
                   d.document_number,d.issuing_authority,d.validity_status,f.create_time
            FROM knowledge_favorite f LEFT JOIN knowledge_doc d ON d.doc_id=f.doc_id
            LEFT JOIN knowledge_chat_session s ON s.session_id=f.session_id
            WHERE f.user_id=? ORDER BY f.create_time DESC LIMIT ?
            """,userId,Math.min(100,Math.max(1,limit)));
    }

    public List<Map<String,Object>> history(int limit) {
        Long userId=KmaIdentityContext.getUserId(); if(userId==null)return List.of();
        return knowledgeJdbcTemplate.queryForList("""
            SELECT h.doc_id,d.title,d.document_number,d.issuing_authority,d.validity_status,h.last_location,
                   h.last_read_at,h.read_count FROM knowledge_read_history h
            JOIN knowledge_doc d ON d.doc_id=h.doc_id
            WHERE h.user_id=?
            """ + " AND " + portalVisibility("d") + " ORDER BY h.last_read_at DESC LIMIT ?",
            userId,Math.min(100,Math.max(1,limit)));
    }

    private void markGoverned(Long id,String title,String contentType,String number,String authority,LocalDate publishDate,
                              LocalDate effectiveDate,LocalDate expiryDate,String validity,String summary,List<String> keywords,List<String> topics) {
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_doc SET title=?,publication_managed=TRUE,content_type=?,document_number=?,issuing_authority=?,
              publish_date=?,effective_date=?,expiry_date=?,validity_status=?,workflow_status='draft',review_decision=NULL,
              online=FALSE,is_active=FALSE,summary=?,keywords=?::jsonb,created_by=COALESCE(created_by,?),update_time=now()
            WHERE doc_id=?
            """,title,contentType,number,authority,publishDate,effectiveDate,expiryDate,validity,summary,
            json(keywords),KmaIdentityContext.getUserId(),id);
        assignTopics(id,topics);
    }

    private void assignTopics(Long docId,List<String> codes) {
        knowledgeJdbcTemplate.update("DELETE FROM knowledge_doc_topic WHERE doc_id=?",docId);
        if(codes==null||codes.isEmpty())return;
        for(String code:new LinkedHashSet<>(codes)) {
            int changed=knowledgeJdbcTemplate.update("""
                INSERT INTO knowledge_doc_topic(doc_id,topic_id)
                SELECT ?,topic_id FROM knowledge_topic WHERE topic_code=? AND enabled=TRUE
                ON CONFLICT DO NOTHING
                """,docId,code);
            if(changed==0)throw new KmaException(400,"TOPIC_INVALID: "+code);
        }
    }

    private PartyContentView queryOne(Long id,boolean portal) {
        String sql=selectColumns()+" FROM knowledge_doc d JOIN knowledge_space s ON s.space_id=d.space_id WHERE d.doc_id=? AND d.publication_managed=TRUE"+(portal?" AND "+portalVisibility("d"):"");
        List<PartyContentView> rows=knowledgeJdbcTemplate.query(sql,(rs,row)->mapContent(rs),id);
        if(rows.isEmpty())throw new KmaException(404,portal?"CONTENT_NOT_AVAILABLE":"CONTENT_NOT_FOUND");
        enrich(rows,portal); return rows.getFirst();
    }

    private String selectColumns(){return """
        SELECT d.doc_id,d.space_id,s.space_code,s.name space_name,d.title,d.source_tag,d.external_ref,d.source_version,
          d.is_active,d.parse_status,d.mime_type,d.content_type,d.document_number,d.issuing_authority,d.publish_date,
          d.effective_date,d.expiry_date,d.scheduled_online_at,d.scheduled_offline_at,d.schedule_note,d.created_by,
          d.validity_status,d.workflow_status,d.review_decision,d.review_note,d.online,
          d.summary,d.keywords,d.reviewer_id,d.submitted_at,d.reviewed_at,d.published_at,d.create_time,d.update_time
        """;}

    private PartyContentView mapContent(java.sql.ResultSet rs) throws java.sql.SQLException {
        PartyContentView v=new PartyContentView();
        v.setContentId(rs.getLong("doc_id"));v.setSpaceId(rs.getLong("space_id"));v.setSpaceCode(rs.getString("space_code"));v.setSpaceName(rs.getString("space_name"));
        v.setTitle(rs.getString("title"));v.setSourceTag(rs.getString("source_tag"));v.setExternalRef(rs.getString("external_ref"));v.setSourceVersion(rs.getLong("source_version"));
        v.setActive(rs.getBoolean("is_active"));v.setParseStatus(rs.getString("parse_status"));v.setMimeType(rs.getString("mime_type"));v.setContentType(rs.getString("content_type"));
        v.setDocumentNumber(rs.getString("document_number"));v.setIssuingAuthority(rs.getString("issuing_authority"));v.setPublishDate(localDate(rs.getObject("publish_date")));
        v.setEffectiveDate(localDate(rs.getObject("effective_date")));v.setExpiryDate(localDate(rs.getObject("expiry_date")));
        v.setScheduledOnlineAt(localDateTime(rs.getObject("scheduled_online_at")));v.setScheduledOfflineAt(localDateTime(rs.getObject("scheduled_offline_at")));
        v.setScheduleNote(rs.getString("schedule_note"));v.setCreatedBy((Long)rs.getObject("created_by"));v.setValidityStatus(rs.getString("validity_status"));
        v.setWorkflowStatus(rs.getString("workflow_status"));v.setReviewDecision(rs.getString("review_decision"));v.setReviewNote(rs.getString("review_note"));v.setOnline(rs.getBoolean("online"));
        v.setSummary(rs.getString("summary"));v.setKeywords(keywords(String.valueOf(rs.getObject("keywords"))));v.setReviewerId((Long)rs.getObject("reviewer_id"));
        v.setSubmittedAt(localDateTime(rs.getObject("submitted_at")));v.setReviewedAt(localDateTime(rs.getObject("reviewed_at")));v.setPublishedAt(localDateTime(rs.getObject("published_at")));
        v.setCreateTime(localDateTime(rs.getObject("create_time")));v.setUpdateTime(localDateTime(rs.getObject("update_time")));return v;
    }

    private void enrich(List<PartyContentView> rows,boolean favorites) {
        if(rows.isEmpty())return; Long userId=KmaIdentityContext.getUserId();
        for(PartyContentView v:rows){
            v.setTopicCodes(knowledgeJdbcTemplate.queryForList("""
                SELECT t.topic_code FROM knowledge_doc_topic dt JOIN knowledge_topic t ON t.topic_id=dt.topic_id
                WHERE dt.doc_id=? ORDER BY t.sort_order,t.topic_id
                """,String.class,v.getContentId()));
            if(favorites&&userId!=null){Integer count=knowledgeJdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_favorite WHERE user_id=? AND favorite_type='content' AND doc_id=?",Integer.class,userId,v.getContentId());v.setFavorite(count!=null&&count>0);}
        }
    }

    private List<Map<String,Object>> versionRows(PartyContentView v){return knowledgeJdbcTemplate.queryForList("""
        SELECT doc_id content_id,source_version,workflow_status,review_decision,online,is_active active,parse_status,published_at,create_time
        FROM knowledge_doc WHERE space_id=? AND external_ref=? ORDER BY source_version DESC,create_time DESC
        """,v.getSpaceId(),v.getExternalRef());}
    private List<Map<String,Object>> relatedRows(PartyContentView v){return knowledgeJdbcTemplate.queryForList("""
        SELECT doc_id content_id,title,document_number,issuing_authority,publish_date,validity_status FROM knowledge_doc
        WHERE content_type=? AND doc_id<>?
        """ + " AND " + portalVisibility("knowledge_doc") + " ORDER BY publish_date DESC NULLS LAST LIMIT 5",v.getContentType(),v.getContentId());}
    private List<Map<String,Object>> categoryCards(){return knowledgeJdbcTemplate.queryForList("""
        SELECT v.code content_type,v.name,COALESCE(c.total,0) total FROM (VALUES
          ('party_constitution','党章党规',10),('policy','政策文件',20),('learning_material','学习材料',30),
          ('grassroots_case','基层案例',40),('organization_system','组织工作制度',50)) v(code,name,sort_order)
        LEFT JOIN (SELECT content_type,count(*) total FROM knowledge_doc WHERE
        """ + portalVisibility("knowledge_doc") + " GROUP BY content_type) c ON c.content_type=v.code ORDER BY v.sort_order");}

    private List<Map<String,Object>> scopedCategoryCards(PortalContentScope scope){
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> row:categoryCards()){
            String contentType=String.valueOf(row.get("content_type"));
            if(!scope.contentTypes().isEmpty()&&!scope.contentTypes().contains(contentType))continue;
            PortalContentQuery query=new PortalContentQuery();query.setContentType(contentType);query.setPageSize(1);
            Map<String,Object> item=new LinkedHashMap<>(row);
            item.put("total",portalPage(query,scope).get("total"));
            result.add(item);
        }
        return result;
    }

    private void assertInSiteScope(Long docId,PortalContentScope scope){
        List<Object> args=new ArrayList<>();
        StringBuilder where=new StringBuilder(" WHERE d.doc_id=? AND "+portalVisibility("d"));
        args.add(docId);
        applySiteScope(where,args,scope,"d","s");
        Integer count=knowledgeJdbcTemplate.queryForObject("""
            SELECT count(*) FROM knowledge_doc d
            JOIN knowledge_space s ON s.space_id=d.space_id
            """+where,Integer.class,args.toArray());
        if(count==null||count==0)throw new KmaException(404,"PORTAL_CONTENT_NOT_FOUND");
    }

    private void applySiteScope(StringBuilder where,List<Object> args,PortalContentScope scope,
                                String docAlias,String spaceAlias){
        if(!scope.allSpaces()){
            if(scope.spaceCodes().isEmpty())where.append(" AND 1=0");
            else in(where,args,spaceAlias+".space_code",scope.spaceCodes());
        }
        if(!scope.contentTypes().isEmpty())in(where,args,docAlias+".content_type",scope.contentTypes());
        if(!scope.validityStatuses().isEmpty())in(where,args,docAlias+".validity_status",scope.validityStatuses());
        if(!scope.topicCodes().isEmpty()){
            where.append("""
                 AND EXISTS (
                   SELECT 1 FROM knowledge_doc_topic site_dt
                   JOIN knowledge_topic site_t ON site_t.topic_id=site_dt.topic_id
                   WHERE site_dt.doc_id=
                """).append(docAlias).append(".doc_id")
                .append(" AND site_t.topic_code IN (")
                .append("?,".repeat(scope.topicCodes().size()));
            where.setLength(where.length()-1);where.append("))");args.addAll(scope.topicCodes());
        }
    }

    private void in(StringBuilder where,List<Object> args,String column,Collection<String> values){
        where.append(" AND ").append(column).append(" IN (").append("?,".repeat(values.size()));
        where.setLength(where.length()-1);where.append(")");args.addAll(values);
    }

    private void recordHistory(Long docId,String location){Long userId=KmaIdentityContext.getUserId();if(userId==null)return;knowledgeJdbcTemplate.update("""
        INSERT INTO knowledge_read_history(user_id,doc_id,last_location) VALUES (?,?,?)
        ON CONFLICT (user_id,doc_id) DO UPDATE SET last_location=EXCLUDED.last_location,last_read_at=now(),read_count=knowledge_read_history.read_count+1
        """,userId,docId,location);}
    private KnowledgeDoc requireManaged(Long id){KnowledgeDoc d=docMapper.selectById(id);if(d==null||!Boolean.TRUE.equals(d.getPublicationManaged()))throw new KmaException(404,"CONTENT_NOT_FOUND");return d;}
    private KnowledgeDoc lockManaged(Long id){knowledgeJdbcTemplate.queryForObject("SELECT doc_id FROM knowledge_doc WHERE doc_id=? FOR UPDATE",Long.class,id);return requireManaged(id);}
    private KnowledgeSpace requireSpace(KnowledgeDoc d){KnowledgeSpace s=spaceMapper.selectById(d.getSpaceId());if(s==null)throw new KmaException(404,"SPACE_NOT_FOUND");return s;}
    private Long requireUser(){Long id=KmaIdentityContext.getUserId();if(id==null)throw new KmaException(403,"USER_IDENTITY_REQUIRED");return id;}
    private String externalRef(String value){return StringUtils.hasText(value)?value:"party-content:"+UUID.randomUUID();}
    private String portalVisibility(String a){return "(("+a+".publication_managed=TRUE AND "+a+".workflow_status='published' AND "+a+".online=TRUE) OR ("+a+".publication_managed=FALSE AND "+a+".is_active=TRUE AND "+a+".parse_status='completed')) AND ("+a+".scheduled_online_at IS NULL OR "+a+".scheduled_online_at<=now()) AND ("+a+".scheduled_offline_at IS NULL OR "+a+".scheduled_offline_at>now()) AND "+a+".validity_status NOT IN ('expired','repealed') AND ("+a+".effective_date IS NULL OR "+a+".effective_date<=CURRENT_DATE) AND ("+a+".expiry_date IS NULL OR "+a+".expiry_date>=CURRENT_DATE)";}
    private void applySchedule(Long id, LocalDateTime onlineAt, LocalDateTime offlineAt, String note){
        if(onlineAt!=null&&offlineAt!=null&&!offlineAt.isAfter(onlineAt))throw new KmaException(400,"CONTENT_SCHEDULE_WINDOW_INVALID");
        knowledgeJdbcTemplate.update("UPDATE knowledge_doc SET scheduled_online_at=?,scheduled_offline_at=?,schedule_note=?,update_time=now() WHERE doc_id=?",onlineAt,offlineAt,note,id);
    }
    private void assertSeparationOfDuties(KnowledgeDoc doc,String stage){
        Boolean enabled=knowledgeJdbcTemplate.queryForObject("SELECT content_separation_of_duties FROM kma_governance_policy WHERE policy_key='default'",Boolean.class);
        Long actor=KmaIdentityContext.getUserId();
        if(Boolean.TRUE.equals(enabled)&&actor!=null&&actor.equals(doc.getCreatedBy()))throw new KmaException(409,"CONTENT_SEPARATION_OF_DUTIES_REQUIRED: "+stage);
    }
    private void readableSpaces(StringBuilder where,List<Object> args,String alias){Set<Long> ids=aclService.getReadableSpaceIds();if(ids==null)return;if(ids.isEmpty()){where.append(" AND 1=0");return;}where.append(" AND ").append(alias).append(".space_id IN (").append(String.join(",",Collections.nCopies(ids.size(),"?"))).append(')');args.addAll(ids);}
    private void eq(StringBuilder where,List<Object> args,String field,String value){if(StringUtils.hasText(value)){where.append(" AND ").append(field).append("=?");args.add(value);}}
    private Map<String,Object> page(List<?> list,long total,int pageNum,int pageSize){Map<String,Object> m=new LinkedHashMap<>();m.put("list",list);m.put("total",total);m.put("pageNum",pageNum);m.put("pageSize",pageSize);return m;}
    private String json(Object value){try{return objectMapper.writeValueAsString(value==null?List.of():value);}catch(Exception e){throw new KmaException(400,"INVALID_JSON_METADATA");}}
    private List<String> keywords(String value){try{return value==null||"null".equals(value)?List.of():objectMapper.readValue(value,new TypeReference<>(){});}catch(Exception e){return List.of();}}
    private LocalDate localDate(Object value){if(value instanceof LocalDate d)return d;if(value instanceof Date d)return d.toLocalDate();return null;}
    private LocalDateTime localDateTime(Object value){if(value instanceof LocalDateTime d)return d;if(value instanceof Timestamp t)return t.toLocalDateTime();return null;}
    private Map<String,Object> snapshot(KnowledgeDoc d){Map<String,Object> m=new LinkedHashMap<>();m.put("title",d.getTitle());m.put("workflowStatus",d.getWorkflowStatus());m.put("reviewDecision",d.getReviewDecision());m.put("online",d.getOnline());m.put("active",d.getIsActive());return m;}
    private Map<String,Object> viewSnapshot(PartyContentView v){Map<String,Object> m=new LinkedHashMap<>();m.put("title",v.getTitle());m.put("workflowStatus",v.getWorkflowStatus());m.put("reviewDecision",v.getReviewDecision());m.put("online",v.getOnline());return m;}
    private Map<String,Object> topicAuditSnapshot(TopicRequest request){Map<String,Object> m=new LinkedHashMap<>();m.put("name",request.getName());m.put("enabled",request.getEnabled());m.put("featured",request.getFeatured());return m;}
}
