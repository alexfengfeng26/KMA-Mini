package com.kma.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.PortalConfigDraftRequest;
import com.kma.knowledge.dto.PortalContentQuery;
import com.kma.knowledge.dto.PortalDataBatchRequest;
import com.kma.knowledge.dto.PortalSiteCreateRequest;
import com.kma.knowledge.dto.PortalSiteUpdateRequest;
import com.kma.knowledge.dto.PortalVersionActionRequest;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.PartyContentView;
import com.kma.knowledge.entity.KnowledgeDoc;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Multi-site CMS aggregate. Publishing is an atomic pointer switch to an immutable validated version. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalSiteService {
    private final JdbcTemplate knowledgeJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PortalSiteConfigValidator validator;
    private final PortalCssScopeService cssScopeService;
    private final PortalExtensionService extensionService;
    private final PortalCodePackageService codePackageService;
    private final PartyKnowledgeService partyKnowledgeService;
    private final SecurityAuditService auditService;

    public List<Map<String, Object>> listSites() {
        return knowledgeJdbcTemplate.queryForList("""
            SELECT s.site_id AS "siteId",s.site_key AS "siteKey",s.name,s.scenario,s.status,
                   s.default_site AS "defaultSite",s.current_published_version_id AS "publishedVersionId",
                   v.version_no AS "publishedVersion",s.create_time AS "createTime",s.update_time AS "updateTime"
            FROM knowledge_portal_site s
            LEFT JOIN knowledge_portal_config_version v
              ON v.config_version_id=s.current_published_version_id
            ORDER BY s.default_site DESC,s.site_id
            """);
    }

    public Map<String, Object> getSite(String siteKey) {
        Site site = requireSite(siteKey, false);
        Map<String, Object> result = new LinkedHashMap<>(site.view());
        result.put("versions", versions(siteKey));
        return result;
    }

    public List<Map<String, Object>> versions(String siteKey) {
        Site site = requireSite(siteKey, false);
        return knowledgeJdbcTemplate.queryForList("""
            SELECT config_version_id AS "versionId",version_no AS "versionNo",status,schema_version AS "schemaVersion",
                   checksum,lock_version AS "lockVersion",change_note AS "changeNote",created_by AS "createdBy",
                   reviewed_by AS "reviewedBy",published_by AS "publishedBy",create_time AS "createTime",
                   submitted_at AS "submittedAt",reviewed_at AS "reviewedAt",published_at AS "publishedAt"
            FROM knowledge_portal_config_version
            WHERE site_id=? ORDER BY version_no DESC
            """, site.siteId());
    }

    public Map<String, Object> version(String siteKey, Long versionId) {
        Site site = requireSite(siteKey, false);
        return version(site.siteId(), versionId, true);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> createSite(PortalSiteCreateRequest request) {
        long userId = userId();
        try {
            if (Boolean.TRUE.equals(request.getDefaultSite()))
                knowledgeJdbcTemplate.update("UPDATE knowledge_portal_site SET default_site=FALSE,update_time=now()");
            Long siteId = knowledgeJdbcTemplate.queryForObject("""
                INSERT INTO knowledge_portal_site
                    (site_key,name,scenario,status,default_site,created_by,updated_by)
                VALUES (?,?,?,'active',?,?,?) RETURNING site_id
                """, Long.class, request.getSiteKey(), request.getName(), request.getScenario(),
                Boolean.TRUE.equals(request.getDefaultSite()), userId, userId);
            JsonNode config = defaultConfig(request.getSiteKey(), request.getName(), request.getScenario());
            insertVersion(siteId, nextVersion(siteId), "draft", config, "场景模板初始化", userId, null, null);
            auditService.recordRequired("portal_configuration", "info", "portal-site.create",
                "portal-site:" + request.getSiteKey(), Map.of(), Map.of(
                    "name", request.getName(), "scenario", request.getScenario()), Map.of());
            return getSite(request.getSiteKey());
        } catch (DuplicateKeyException ex) {
            throw new KmaException(409, "PORTAL_SITE_ALREADY_EXISTS");
        }
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> updateSite(String siteKey, PortalSiteUpdateRequest request) {
        Site before = requireSite(siteKey, false);
        if (before.defaultSite() && "disabled".equals(request.getStatus()))
            throw new KmaException(409, "DEFAULT_PORTAL_SITE_CANNOT_BE_DISABLED");
        if (Boolean.TRUE.equals(request.getDefaultSite()))
            knowledgeJdbcTemplate.update("UPDATE knowledge_portal_site SET default_site=FALSE,update_time=now()");
        int changed = knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_site
            SET name=?,status=?,default_site=?,updated_by=?,update_time=now()
            WHERE site_key=?
            """, request.getName(), request.getStatus(), Boolean.TRUE.equals(request.getDefaultSite()),
            userId(), siteKey);
        if (changed == 0) throw new KmaException(404, "PORTAL_SITE_NOT_FOUND");
        auditService.recordRequired("portal_configuration", "info", "portal-site.update",
            "portal-site:" + siteKey, before.view(), Map.of(
                "name", request.getName(), "status", request.getStatus(),
                "defaultSite", Boolean.TRUE.equals(request.getDefaultSite())), Map.of());
        return getSite(siteKey);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void deleteSite(String siteKey) {
        Site site = requireSite(siteKey, false);
        if (site.defaultSite()) throw new KmaException(409, "DEFAULT_PORTAL_SITE_REQUIRED");
        auditService.recordRequired("portal_configuration", "warning", "portal-site.delete",
            "portal-site:" + siteKey, site.view(), Map.of(), Map.of());
        knowledgeJdbcTemplate.update("DELETE FROM knowledge_portal_site WHERE site_id=?", site.siteId());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> createDraft(String siteKey, PortalConfigDraftRequest request) {
        Site site = requireSite(siteKey, false);
        JsonNode config = request == null ? null : request.getConfig();
        if (config == null) config = latestConfig(site.siteId());
        if (config == null) config = defaultConfig(site.siteKey(), site.name(), site.scenario());
        int versionNo = nextVersion(site.siteId());
        Long id = insertVersion(site.siteId(), versionNo, "draft", config,
            request == null ? null : request.getChangeNote(), userId(), null, null);
        auditService.recordRequired("portal_configuration", "info", "portal-site.draft.create",
            "portal-version:" + id, Map.of(), Map.of("siteKey", siteKey, "versionNo", versionNo), Map.of());
        return version(site.siteId(), id, true);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> updateDraft(String siteKey, Long versionId, PortalConfigDraftRequest request) {
        Site site = requireSite(siteKey, false);
        if (request.getConfig() == null) throw new KmaException(400, "PORTAL_CONFIG_REQUIRED");
        if (request.getExpectedLockVersion() == null) throw new KmaException(400, "PORTAL_LOCK_VERSION_REQUIRED");
        Map<String, Object> before = version(site.siteId(), versionId, false);
        int changed = knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_config_version
            SET schema_version=?,config_json=?::jsonb,checksum=?,change_note=?,lock_version=lock_version+1
            WHERE site_id=? AND config_version_id=? AND status='draft' AND lock_version=?
            """, schemaVersion(request.getConfig()), json(request.getConfig()), checksum(request.getConfig()),
            request.getChangeNote(),
            site.siteId(), versionId, request.getExpectedLockVersion());
        if (changed == 0) throw new KmaException(409, "PORTAL_VERSION_CONFLICT");
        auditService.recordRequired("portal_configuration", "info", "portal-site.draft.update",
            "portal-version:" + versionId, Map.of("checksum", before.get("checksum")),
            Map.of("checksum", checksum(request.getConfig())), Map.of("siteKey", siteKey));
        return version(site.siteId(), versionId, true);
    }

    public Map<String, Object> validate(String siteKey, PortalVersionActionRequest request) {
        Site site = requireSite(siteKey, false);
        JsonNode config = config(site.siteId(), request.getVersionId(), false);
        List<String> issues = validationIssues(config, siteKey);
        return Map.of("valid", issues.isEmpty(), "issues", issues, "schemaVersion", schemaVersion(config),
            "checksum", checksum(config));
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void submit(String siteKey, PortalVersionActionRequest request) {
        Site site = requireSite(siteKey, false);
        JsonNode config = config(site.siteId(), request.getVersionId(), false);
        assertValid(config, siteKey);
        int changed = knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_config_version
            SET status='reviewing',change_note=?,submitted_at=now(),reviewed_at=NULL,reviewed_by=NULL,
                lock_version=lock_version+1
            WHERE site_id=? AND config_version_id=? AND status='draft'
            """, request.getNote(), site.siteId(), request.getVersionId());
        if (changed == 0) throw new KmaException(409, "PORTAL_VERSION_NOT_DRAFT");
        audit("portal-site.submit", siteKey, request.getVersionId());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void approve(String siteKey, PortalVersionActionRequest request) {
        Site site = requireSite(siteKey, false);
        int changed = knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_config_version
            SET reviewed_by=?,reviewed_at=now(),change_note=COALESCE(?,change_note),lock_version=lock_version+1
            WHERE site_id=? AND config_version_id=? AND status='reviewing'
            """, userId(), request.getNote(), site.siteId(), request.getVersionId());
        if (changed == 0) throw new KmaException(409, "PORTAL_VERSION_NOT_REVIEWING");
        audit("portal-site.approve", siteKey, request.getVersionId());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void reject(String siteKey, PortalVersionActionRequest request) {
        Site site = requireSite(siteKey, false);
        int changed = knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_config_version
            SET status='draft',reviewed_by=?,reviewed_at=now(),change_note=?,lock_version=lock_version+1
            WHERE site_id=? AND config_version_id=? AND status='reviewing'
            """, userId(), request.getNote(), site.siteId(), request.getVersionId());
        if (changed == 0) throw new KmaException(409, "PORTAL_VERSION_NOT_REVIEWING");
        audit("portal-site.reject", siteKey, request.getVersionId());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void publish(String siteKey, PortalVersionActionRequest request) {
        Site site = lockSite(siteKey);
        Map<String, Object> candidate = version(site.siteId(), request.getVersionId(), false);
        if (!"reviewing".equals(candidate.get("status")) || candidate.get("reviewedAt") == null)
            throw new KmaException(409, "PORTAL_VERSION_NOT_APPROVED");
        JsonNode config = preparePublishedConfig(config(site.siteId(), request.getVersionId(), false), siteKey);
        assertValid(config, siteKey);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_config_version SET config_json=?::jsonb,checksum=?
            WHERE site_id=? AND config_version_id=?
            """, json(config), checksum(config), site.siteId(), request.getVersionId());
        compileScope(site, request.getVersionId(), config);
        extensionService.compileUsage(site.siteId(), request.getVersionId(), config);
        codePackageService.compileUsage(site.siteId(), request.getVersionId(), config);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_config_version SET status='archived'
            WHERE site_id=? AND status='published'
            """, site.siteId());
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_config_version
            SET status='published',published_by=?,published_at=now(),change_note=COALESCE(?,change_note),
                lock_version=lock_version+1
            WHERE site_id=? AND config_version_id=?
            """, userId(), request.getNote(), site.siteId(), request.getVersionId());
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_site
            SET current_published_version_id=?,updated_by=?,update_time=now()
            WHERE site_id=?
            """, request.getVersionId(), userId(), site.siteId());
        audit("portal-site.publish", siteKey, request.getVersionId());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> rollback(String siteKey, Long sourceVersionId) {
        Site site = lockSite(siteKey);
        JsonNode source = preparePublishedConfig(config(site.siteId(), sourceVersionId, true), siteKey);
        assertValid(source, siteKey);
        int versionNo = nextVersion(site.siteId());
        Long versionId = insertVersion(site.siteId(), versionNo, "published", source,
            "回滚自版本 " + sourceVersionId, userId(), userId(), userId());
        compileScope(site, versionId, source);
        extensionService.compileUsage(site.siteId(), versionId, source);
        codePackageService.compileUsage(site.siteId(), versionId, source);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_config_version SET status='archived'
            WHERE site_id=? AND status='published' AND config_version_id<>?
            """, site.siteId(), versionId);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_site SET current_published_version_id=?,updated_by=?,update_time=now()
            WHERE site_id=?
            """, versionId, userId(), site.siteId());
        audit("portal-site.rollback", siteKey, versionId);
        return version(site.siteId(), versionId, true);
    }

    public Map<String, Object> bootstrap(String siteKey, String pageSlug) {
        Published published = published(siteKey);
        JsonNode page = published.config().path("pages").path(
            StringUtils.hasText(pageSlug) ? pageSlug : "home");
        if (page.isMissingNode()) throw new KmaException(404, "PORTAL_PAGE_NOT_FOUND");
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> siteView = new LinkedHashMap<>(published.site().view());
        JsonNode configuredSite = published.config().path("site");
        siteView.put("name", configuredSite.path("name").asText(published.site().name()));
        siteView.put("scenario", configuredSite.path("scenario").asText(published.site().scenario()));
        siteView.put("locale", configuredSite.path("locale").asText("zh-CN"));
        result.put("site", siteView);
        result.put("publishedVersion", published.versionNo());
        result.put("schemaVersion", schemaVersion(published.config()));
        result.put("revision", published.config().path("revision").asText("published-" + published.versionNo()));
        result.put("shell", published.config().path("shell"));
        result.put("theme", published.config().path("theme"));
        result.put("modules", published.config().path("modules"));
        result.put("search", published.config().path("search"));
        result.put("assistant", published.config().path("assistant"));
        result.put("page", page);
        result.put("symbols", published.config().path("symbols"));
        result.put("packages", published.config().path("packages"));
        List<Map<String, Object>> resolvedExtensions = new ArrayList<>(extensionService.resolveBindings(page));
        resolvedExtensions.addAll(codePackageService.resolveBindings(page));
        result.put("extensions", resolvedExtensions);
        result.put("portalData", partyKnowledgeService.home(published.scope()));
        return result;
    }

    public JsonNode page(String siteKey, String pageSlug) {
        Published published = published(siteKey);
        JsonNode page = published.config().path("pages").path(pageSlug);
        if (page.isMissingNode()) throw new KmaException(404, "PORTAL_PAGE_NOT_FOUND");
        return page;
    }

    public Map<String, Object> contents(String siteKey, PortalContentQuery query) {
        return partyKnowledgeService.portalPage(query, published(siteKey).scope());
    }

    public Map<String, Object> batchData(String siteKey, PortalDataBatchRequest request) {
        Published published = published(siteKey);
        Map<String, Object> home = partyKnowledgeService.home(published.scope());
        Map<String, Object> result = new LinkedHashMap<>();
        for (PortalDataBatchRequest.Query item : request.getQueries()) {
            Map<String, String> filters = item.getFilters() == null ? Map.of() : item.getFilters();
            Object value = switch (item.getSource()) {
                case "documents" -> partyKnowledgeService.portalPage(toContentQuery(filters), published.scope());
                case "categories" -> home.getOrDefault("categories", List.of());
                case "topics" -> home.getOrDefault("topics", List.of());
                case "favorites" -> home.getOrDefault("favorites", List.of());
                case "history" -> home.getOrDefault("history", List.of());
                case "announcements" -> List.of();
                case "static" -> Map.of();
                default -> throw new KmaException(400, "PORTAL_DATA_SOURCE_FORBIDDEN");
            };
            result.put(item.getId(), value);
        }
        return Map.of("results", result, "revision", published.config().path("revision").asText(""));
    }

    private PortalContentQuery toContentQuery(Map<String, String> filters) {
        PortalContentQuery query = new PortalContentQuery();
        query.setKeyword(filters.get("keyword"));
        query.setContentType(filters.get("contentType"));
        query.setTopicCode(filters.get("topicCode"));
        query.setValidityStatus(filters.get("validityStatus"));
        query.setSpaceCode(filters.get("spaceCode"));
        query.setPageNum(parseBounded(filters.get("pageNum"), 1, 1, 100000));
        query.setPageSize(parseBounded(filters.get("pageSize"), 20, 1, 100));
        query.setIncludeHistorical(Boolean.parseBoolean(filters.getOrDefault("includeHistorical", "false")));
        return query;
    }

    private int parseBounded(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public PartyContentView content(String siteKey, Long contentId, String location) {
        return partyKnowledgeService.getPortalContent(contentId, location, published(siteKey).scope());
    }

    public KnowledgeDoc document(String siteKey, Long contentId) {
        content(siteKey, contentId, null);
        return partyKnowledgeService.getPortalDocument(contentId);
    }

    public void secureQa(String siteKey, QARequest request) {
        PortalContentScope scope = published(siteKey).scope();
        if (!scope.allSpaces() && !scope.spaceCodes().contains(request.getSpaceCode()))
            throw new KmaException(403, "PORTAL_SPACE_OUT_OF_SCOPE");
        request.setPortalOnly(true);
        request.setContentTypes(intersection(request.getContentTypes(), scope.contentTypes(), "PORTAL_CONTENT_TYPE_OUT_OF_SCOPE"));
        request.setTopicCodes(intersection(request.getTopicCodes(), scope.topicCodes(), "PORTAL_TOPIC_OUT_OF_SCOPE"));
        Set<String> allowedValidity = scope.validityStatuses().isEmpty()
            ? Set.of("effective", "pending") : scope.validityStatuses();
        request.setValidityStatuses(intersection(request.getValidityStatuses(), allowedValidity,
            "PORTAL_VALIDITY_OUT_OF_SCOPE"));
    }

    private Published published(String siteKey) {
        Site site = requireSite(siteKey, true);
        if (site.publishedVersionId() == null) throw new KmaException(404, "PORTAL_SITE_NOT_PUBLISHED");
        JsonNode config = config(site.siteId(), site.publishedVersionId(), true);
        Integer versionNo = knowledgeJdbcTemplate.queryForObject("""
            SELECT version_no FROM knowledge_portal_config_version
            WHERE site_id=? AND config_version_id=? AND status='published'
            """, Integer.class, site.siteId(), site.publishedVersionId());
        return new Published(site, versionNo == null ? 0 : versionNo, config,
            loadScope(site.siteId(), site.publishedVersionId()));
    }

    private PortalContentScope loadScope(Long siteId, Long versionId) {
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT scope_type AS "scopeType",scope_value AS "scopeValue"
            FROM knowledge_portal_site_scope
            WHERE site_id=? AND config_version_id=?
            """, siteId, versionId);
        boolean all = rows.stream().anyMatch(row -> "all".equals(row.get("scopeType")));
        return new PortalContentScope(all, values(rows, "space"), values(rows, "topic"),
            values(rows, "content_type"), values(rows, "validity"));
    }

    private Set<String> values(List<Map<String, Object>> rows, String type) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        rows.stream().filter(row -> type.equals(row.get("scopeType")))
            .map(row -> String.valueOf(row.get("scopeValue"))).forEach(result::add);
        return result;
    }

    private void compileScope(Site site, Long versionId, JsonNode config) {
        knowledgeJdbcTemplate.update("""
            DELETE FROM knowledge_portal_site_scope
            WHERE site_id=? AND config_version_id=?
            """, site.siteId(), versionId);
        JsonNode scope = config.path("contentScope");
        if (scope.path("allSpaces").asBoolean(false)) insertScope(site.siteId(), versionId, "all", "*");
        insertScopeArray(site.siteId(), versionId, "space", scope.path("spaceCodes"));
        insertScopeArray(site.siteId(), versionId, "topic", scope.path("topicCodes"));
        insertScopeArray(site.siteId(), versionId, "content_type", scope.path("contentTypes"));
        insertScopeArray(site.siteId(), versionId, "validity", scope.path("validityStatuses"));
    }

    private void insertScopeArray(Long siteId, Long versionId, String type, JsonNode values) {
        if (!values.isArray()) return;
        values.forEach(value -> {
            if (StringUtils.hasText(value.asText())) insertScope(siteId, versionId, type, value.asText());
        });
    }

    private void insertScope(Long siteId, Long versionId, String type, String value) {
        knowledgeJdbcTemplate.update("""
            INSERT INTO knowledge_portal_site_scope(site_id,config_version_id,scope_type,scope_value)
            VALUES (?,?,?,?) ON CONFLICT DO NOTHING
            """, siteId, versionId, type, value);
    }

    private Site requireSite(String siteKey, boolean activeOnly) {
        List<Site> rows = knowledgeJdbcTemplate.query("""
            SELECT site_id,site_key,name,scenario,status,default_site,current_published_version_id
            FROM knowledge_portal_site WHERE site_key=?
            """ + (activeOnly ? " AND status='active'" : ""),
            (rs, rowNum) -> new Site(rs.getLong("site_id"), rs.getString("site_key"),
                rs.getString("name"), rs.getString("scenario"), rs.getString("status"),
                rs.getBoolean("default_site"), (Long) rs.getObject("current_published_version_id")),
            siteKey);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_SITE_NOT_FOUND");
        return rows.getFirst();
    }

    private Site lockSite(String siteKey) {
        List<Site> rows = knowledgeJdbcTemplate.query("""
            SELECT site_id,site_key,name,scenario,status,default_site,current_published_version_id
            FROM knowledge_portal_site WHERE site_key=? FOR UPDATE
            """, (rs, rowNum) -> new Site(rs.getLong("site_id"), rs.getString("site_key"),
            rs.getString("name"), rs.getString("scenario"), rs.getString("status"),
            rs.getBoolean("default_site"), (Long) rs.getObject("current_published_version_id")),
            siteKey);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_SITE_NOT_FOUND");
        return rows.getFirst();
    }

    private Map<String, Object> version(Long siteId, Long versionId, boolean includeConfig) {
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT config_version_id AS "versionId",version_no AS "versionNo",status,
                   schema_version AS "schemaVersion",checksum,lock_version AS "lockVersion",
                   change_note AS "changeNote",create_time AS "createTime",submitted_at AS "submittedAt",
                   reviewed_at AS "reviewedAt",published_at AS "publishedAt"
            FROM knowledge_portal_config_version
            WHERE site_id=? AND config_version_id=?
            """, siteId, versionId);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_VERSION_NOT_FOUND");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        if (includeConfig) result.put("config", config(siteId, versionId, true));
        return result;
    }

    private JsonNode config(Long siteId, Long versionId, boolean allowArchived) {
        List<String> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT config_json::text FROM knowledge_portal_config_version
            WHERE site_id=? AND config_version_id=?
            """ + (allowArchived ? "" : " AND status IN ('draft','reviewing','published')"),
            String.class, siteId, versionId);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_VERSION_NOT_FOUND");
        try {
            return objectMapper.readTree(rows.getFirst());
        } catch (JsonProcessingException ex) {
            throw new KmaException(500, "PORTAL_CONFIG_CORRUPTED");
        }
    }

    private JsonNode latestConfig(Long siteId) {
        List<String> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT config_json::text FROM knowledge_portal_config_version
            WHERE site_id=?
            ORDER BY CASE status WHEN 'published' THEN 0 ELSE 1 END,version_no DESC LIMIT 1
            """, String.class, siteId);
        if (rows.isEmpty()) return null;
        try {
            return objectMapper.readTree(rows.getFirst());
        } catch (JsonProcessingException ex) {
            throw new KmaException(500, "PORTAL_CONFIG_CORRUPTED");
        }
    }

    private Long insertVersion(Long siteId, int versionNo, String status, JsonNode config, String note,
                               Long creator, Long reviewer, Long publisher) {
        return knowledgeJdbcTemplate.queryForObject("""
            INSERT INTO knowledge_portal_config_version
              (site_id,version_no,status,schema_version,config_json,checksum,change_note,
               created_by,reviewed_by,published_by,reviewed_at,published_at)
            VALUES (?,?,?,?,?::jsonb,?,?,?,?,?,CASE WHEN ?::bigint IS NULL THEN NULL ELSE now() END,
                    CASE WHEN ?::bigint IS NULL THEN NULL ELSE now() END)
            RETURNING config_version_id
            """, Long.class, siteId, versionNo, status, schemaVersion(config), json(config), checksum(config), note,
            creator, reviewer, publisher, reviewer, publisher);
    }

    private int schemaVersion(JsonNode config) {
        int version = config == null ? -1 : config.path("schemaVersion").asInt(-1);
        if (version != 2 && version != 3) throw new KmaException(400, "PORTAL_SCHEMA_VERSION_UNSUPPORTED");
        return version;
    }

    private int nextVersion(Long siteId) {
        Integer value = knowledgeJdbcTemplate.queryForObject("""
            SELECT COALESCE(max(version_no),0)+1 FROM knowledge_portal_config_version
            WHERE site_id=?
            """, Integer.class, siteId);
        return value == null ? 1 : value;
    }

    private void assertValid(JsonNode config, String siteKey) {
        List<String> issues = validationIssues(config, siteKey);
        if (!issues.isEmpty()) throw new KmaException(400, "PORTAL_CONFIG_INVALID: " + String.join("; ", issues));
    }

    private List<String> validationIssues(JsonNode config, String siteKey) {
        List<String> issues = new ArrayList<>(validator.validate(config, siteKey));
        issues.addAll(extensionService.validateReferences(config));
        issues.addAll(codePackageService.validateReferences(config));
        return List.copyOf(issues);
    }

    private JsonNode preparePublishedConfig(JsonNode source, String siteKey) {
        JsonNode copy = source.deepCopy();
        if (copy.path("theme").isObject()) {
            String customCss = copy.path("theme").path("customCss").asText("");
            ((com.fasterxml.jackson.databind.node.ObjectNode) copy.path("theme"))
                .put("scopedCss", cssScopeService.scope(siteKey, customCss));
        }
        return copy;
    }

    private List<String> intersection(List<String> requested, Set<String> allowed, String error) {
        if (allowed.isEmpty()) return requested;
        if (requested == null || requested.isEmpty()) return List.copyOf(allowed);
        List<String> result = requested.stream().filter(allowed::contains).distinct().toList();
        if (result.isEmpty()) throw new KmaException(403, error);
        return result;
    }

    private JsonNode defaultConfig(String siteKey, String name, String scenario) {
        String categoryLabel = switch (scenario) {
            case "internal-policy" -> "制度分类";
            case "product-help" -> "产品分类";
            default -> "知识分类";
        };
        String assistant = switch (scenario) {
            case "internal-policy" -> "请描述岗位、制度或流程问题，我会引用现行制度回答。";
            case "product-help" -> "请描述产品使用或故障问题，我会引用帮助文档回答。";
            default -> "请提出党建业务问题，我会引用已发布的权威材料回答。";
        };
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", 2);
        value.put("revision", "draft-" + System.currentTimeMillis());
        value.put("site", Map.of("siteKey", siteKey, "scenario", scenario, "name", name, "locale", "zh-CN"));
        String visualPack = switch (scenario) {
            case "internal-policy" -> "policy-workbench";
            case "product-help" -> "help-product";
            default -> "party-authority";
        };
        String shellLayout = switch (scenario) {
            case "internal-policy" -> "sidebar-workbench";
            case "product-help" -> "search-center";
            default -> "editorial-authority";
        };
        value.put("shell", Map.of(
            "layout", shellLayout,
            "header", Map.of("showSearch", true),
            "navigation", List.of(
                Map.of("id", "home", "label", "首页", "target", "home"),
                Map.of("id", "library", "label", "资料中心", "target", "library"),
                Map.of("id", "ask", "label", "AI 问答", "target", "ask"),
                Map.of("id", "topics", "label", categoryLabel, "target", "topics")),
            "footer", Map.of("text", "内部知识服务 · 回答请核对引用来源")));
        value.put("theme", Map.of("pack", visualPack, "preset", "emerald", "mode", "light", "density", "compact",
            "tokens", Map.of(), "customCss", ""));
        value.put("modules", Map.of());
        value.put("contentScope", Map.of("allSpaces", true, "spaceCodes", List.of(),
            "topicCodes", List.of(), "contentTypes", List.of(),
            "validityStatuses", List.of("effective", "pending")));
        value.put("search", Map.of("placeholder", "搜索标题、正文或文号", "hotKeywords", List.of(),
            "defaultMode", "hybrid"));
        value.put("assistant", Map.of("enabled", true, "title", "AI 知识助手",
            "welcomeText", assistant, "suggestedQuestions", List.of()));
        value.put("pages", Map.of("home", Map.of("slug", "home", "layout", "twelve-grid",
            "regions", Map.of("main", List.of(
                block("hero", "hero-search", "compact", 12, null),
                block("categories", "category-grid", "cards", 12, Map.of("columns", 5)),
                block("recent", "recent-documents", "list", 8, Map.of("limit", 8)),
                block("topic", "current-topic", "card", 4, null),
                block("history", "reading-history", "compact", 4, Map.of("limit", 5)),
                block("favorites", "favorites", "compact", 4, Map.of("limit", 5)))))));
        return objectMapper.valueToTree(value);
    }

    private Map<String, Object> block(String id, String type, String variant, int span, Map<String, Object> props) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", id);
        block.put("type", type);
        block.put("enabled", true);
        block.put("variant", variant);
        block.put("span", span);
        if (props != null) block.put("props", props);
        return block;
    }

    private String checksum(JsonNode config) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json(config).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new KmaException(500, "PORTAL_CHECKSUM_FAILED");
        }
    }

    private String json(JsonNode config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new KmaException(400, "PORTAL_CONFIG_INVALID_JSON");
        }
    }

    private long userId() {
        Long value = KmaIdentityContext.getUserId();
        if (value == null) throw new KmaException(401, "USER_IDENTITY_REQUIRED");
        return value;
    }

    private void audit(String action, String siteKey, Long versionId) {
        auditService.recordRequired("portal_configuration", "info", action,
            "portal-version:" + versionId, Map.of(), Map.of(
                "siteKey", siteKey, "versionId", versionId), Map.of());
    }

    private record Site(Long siteId, String siteKey, String name, String scenario, String status,
                        boolean defaultSite, Long publishedVersionId) {
        Map<String, Object> view() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("siteId", siteId);
            result.put("siteKey", siteKey);
            result.put("name", name);
            result.put("scenario", scenario);
            result.put("status", status);
            result.put("defaultSite", defaultSite);
            result.put("publishedVersionId", publishedVersionId);
            return result;
        }
    }

    private record Published(Site site, int versionNo, JsonNode config, PortalContentScope scope) {}
}
