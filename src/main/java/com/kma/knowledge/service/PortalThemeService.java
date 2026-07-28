package com.kma.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.PortalThemeFilesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalThemeService {
    private final JdbcTemplate knowledgeJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityAuditService auditService;

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> workspace(String siteKey) {
        Map<String, Object> site = requireSite(siteKey);
        Long siteId = ((Number) site.get("siteId")).longValue();
        Map<String, Object> theme = ensureTheme(site);
        List<Map<String, Object>> drafts = knowledgeJdbcTemplate.queryForList("""
            SELECT theme_version_id AS "themeVersionId",version_no AS "versionNo",status,
                   manifest_json AS manifest,checksum,scan_status AS "scanStatus",
                   scan_result AS "scanResult",lock_version AS "lockVersion"
            FROM knowledge_portal_theme_version
            WHERE theme_id=? AND status='draft' ORDER BY version_no DESC LIMIT 1
            """, theme.get("themeId"));
        Long themeVersionId;
        if (drafts.isEmpty()) themeVersionId = clonePublishedTheme(theme, siteId);
        else themeVersionId = ((Number) drafts.getFirst().get("themeVersionId")).longValue();
        Long portalVersionId = ensurePortalDraft(siteId, themeVersionId);
        return workspaceView(site, theme, themeVersionId, portalVersionId);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> save(String siteKey, Long themeVersionId, PortalThemeFilesRequest request) {
        Map<String, Object> site = requireSite(siteKey);
        Long siteId = ((Number) site.get("siteId")).longValue();
        Map<String, Object> theme = requireTheme(siteId);
        Map<String, String> files = request.getFiles();
        JsonNode manifest = request.getManifest() == null ? objectMapper.createObjectNode() : request.getManifest();
        List<String> issues = PortalThemeSecurity.validate(files, manifest);
        long expanded = files.values().stream()
            .mapToLong(value -> (value == null ? "" : value).getBytes(StandardCharsets.UTF_8).length).sum();
        int changed = knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_theme_version
            SET manifest_json=?::jsonb,compiled_json=?::jsonb,checksum=?,scan_status=?,
                scan_result=?::jsonb,file_count=?,expanded_size=?,lock_version=lock_version+1,scanned_at=now()
            WHERE theme_id=? AND theme_version_id=? AND status='draft' AND lock_version=?
            """, json(manifest), json(Map.of("compiler", "kma-liquid-v1", "validated", issues.isEmpty())),
            checksum(files), issues.isEmpty() ? "passed" : "failed", json(Map.of("issues", issues)),
            files.size(), expanded, theme.get("themeId"), themeVersionId, request.getExpectedLockVersion());
        if (changed == 0) throw new KmaException(409, "PORTAL_THEME_VERSION_CONFLICT");
        knowledgeJdbcTemplate.update(
            "DELETE FROM knowledge_portal_theme_file WHERE theme_version_id=?", themeVersionId);
        files.forEach((path, content) -> insertFile(themeVersionId, path, content == null ? "" : content));
        Long portalVersionId = ensurePortalDraft(siteId, themeVersionId);
        auditService.recordRequired("portal_theme", issues.isEmpty() ? "info" : "warning", "portal-theme.save",
            "portal-theme-version:" + themeVersionId, Map.of(), Map.of(
                "siteKey", siteKey, "checksum", checksum(files), "issues", issues), Map.of());
        return workspaceView(site, theme, themeVersionId, portalVersionId);
    }

    public void assertEditable(String siteKey, Long themeVersionId, Integer expectedLockVersion) {
        Integer count = knowledgeJdbcTemplate.queryForObject("""
            SELECT count(*)
            FROM knowledge_portal_theme_version v
            JOIN knowledge_portal_theme t ON t.theme_id=v.theme_id
            JOIN knowledge_portal_site s ON s.site_id=t.site_id
            WHERE s.site_key=? AND v.theme_version_id=? AND v.status='draft' AND v.lock_version=?
            """, Integer.class, siteKey, themeVersionId, expectedLockVersion);
        if (count == null || count == 0) throw new KmaException(409, "PORTAL_THEME_VERSION_CONFLICT");
    }

    public byte[] exportZip(String siteKey, Long themeVersionId) {
        requireOwnedVersion(siteKey, themeVersionId);
        String manifest = knowledgeJdbcTemplate.queryForObject("""
            SELECT manifest_json::text FROM knowledge_portal_theme_version WHERE theme_version_id=?
            """, String.class, themeVersionId);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                writeZip(zip, "theme.json", value(manifest).getBytes(StandardCharsets.UTF_8));
                for (Map.Entry<String, String> entry : files(themeVersionId).entrySet())
                    writeZip(zip, entry.getKey(), exportContent(entry.getValue()));
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new KmaException(500, "PORTAL_THEME_EXPORT_FAILED");
        }
    }

    public Map<String, Object> importZip(
        String siteKey, Long themeVersionId, Integer expectedLockVersion, MultipartFile upload) {
        assertEditable(siteKey, themeVersionId, expectedLockVersion);
        if (upload == null || upload.isEmpty()) throw new KmaException(400, "PORTAL_THEME_ZIP_REQUIRED");
        Map<String, String> imported = new LinkedHashMap<>();
        JsonNode manifest = objectMapper.createObjectNode();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(upload.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String path = PortalThemeSecurity.normalizePath(entry.getName());
                byte[] content = zip.readNBytes(1_048_577);
                if (content.length > 1_048_576) throw new KmaException(413, "PORTAL_THEME_FILE_TOO_LARGE");
                total += content.length;
                if (total > PortalThemeSecurity.MAX_BYTES) throw new KmaException(413, "PORTAL_THEME_TOO_LARGE");
                if ("theme.json".equals(path)) {
                    manifest = objectMapper.readTree(content);
                } else {
                    imported.put(path, importContent(path, content));
                }
                if (imported.size() > PortalThemeSecurity.MAX_FILES)
                    throw new KmaException(413, "PORTAL_THEME_TOO_MANY_FILES");
            }
        } catch (KmaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new KmaException(400, "PORTAL_THEME_ZIP_INVALID");
        }
        PortalThemeFilesRequest request = new PortalThemeFilesRequest();
        request.setFiles(imported);
        request.setManifest(manifest);
        request.setExpectedLockVersion(expectedLockVersion);
        return save(siteKey, themeVersionId, request);
    }

    public Map<String, Object> diff(String siteKey, Long fromVersionId, Long toVersionId) {
        requireOwnedVersion(siteKey, fromVersionId);
        requireOwnedVersion(siteKey, toVersionId);
        Map<String, String> before = files(fromVersionId);
        Map<String, String> after = files(toVersionId);
        java.util.Set<String> paths = new java.util.TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String path : paths) {
            String previous = before.get(path);
            String next = after.get(path);
            if (java.util.Objects.equals(previous, next)) continue;
            changes.add(Map.of(
                "path", path,
                "change", previous == null ? "added" : next == null ? "deleted" : "modified",
                "beforeChecksum", previous == null ? "" : checksum(Map.of(path, previous)),
                "afterChecksum", next == null ? "" : checksum(Map.of(path, next))));
        }
        return Map.of("fromVersionId", fromVersionId, "toVersionId", toVersionId, "changes", changes);
    }

    public List<String> validateReference(JsonNode config) {
        if (config == null || config.path("schemaVersion").asInt() != 4) return List.of();
        long versionId = config.path("theme").path("versionId").asLong(0);
        long themeId = config.path("theme").path("themeId").asLong(0);
        if (versionId <= 0) return List.of("Portal Theme V4 缺少 theme.versionId");
        Integer count = knowledgeJdbcTemplate.queryForObject("""
            SELECT count(*) FROM knowledge_portal_theme_version v
            JOIN knowledge_portal_theme t ON t.theme_id=v.theme_id
            JOIN knowledge_portal_site s ON s.site_id=t.site_id
            WHERE v.theme_version_id=? AND v.theme_id=? AND s.site_key=?
              AND v.scan_status='passed' AND v.status IN ('draft','published','archived')
            """, Integer.class, versionId, themeId, config.path("site").path("siteKey").asText());
        if (count == null || count == 0) return List.of("主题版本不存在、安全扫描未通过或不属于当前站点");
        Map<String, String> themeFiles = files(versionId);
        List<String> issues = new ArrayList<>();
        config.path("routes").fields().forEachRemaining(entry -> {
            if (!themeFiles.containsKey(entry.getValue().asText()))
                issues.add("主题缺少路由模板: " + entry.getKey());
        });
        return List.copyOf(issues);
    }

    public Map<String, Object> runtime(JsonNode config, boolean preview) {
        if (config == null || config.path("schemaVersion").asInt() != 4) return Map.of();
        long versionId = config.path("theme").path("versionId").asLong(0);
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT v.theme_version_id AS "versionId",v.version_no AS "versionNo",v.status,
                   v.manifest_json::text AS "manifestJson",v.checksum,
                   t.theme_key AS "themeKey",t.display_name AS "displayName"
            FROM knowledge_portal_theme_version v
            JOIN knowledge_portal_theme t ON t.theme_id=v.theme_id
            WHERE v.theme_version_id=? AND v.scan_status='passed'
              AND v.status IN (""" + (preview ? "'draft','published','archived'" : "'published'") + ")",
            versionId);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_THEME_NOT_AVAILABLE");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("manifest", parseJson(result.remove("manifestJson")));
        result.put("files", files(versionId));
        return result;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void compileUsage(Long siteId, Long configVersionId, JsonNode config) {
        knowledgeJdbcTemplate.update(
            "DELETE FROM knowledge_portal_theme_usage WHERE site_id=? AND config_version_id=?",
            siteId, configVersionId);
        if (config.path("schemaVersion").asInt() != 4) return;
        long versionId = config.path("theme").path("versionId").asLong();
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT theme_id AS "themeId",status,scan_status AS "scanStatus"
            FROM knowledge_portal_theme_version WHERE theme_version_id=?
            """, versionId);
        if (rows.isEmpty() || !"passed".equals(rows.getFirst().get("scanStatus")))
            throw new KmaException(400, "PORTAL_THEME_SCAN_REQUIRED");
        long themeId = ((Number) rows.getFirst().get("themeId")).longValue();
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_theme_version SET status='archived'
            WHERE theme_id=? AND status='published' AND theme_version_id<>?
            """, themeId, versionId);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_theme_version
            SET status='published',published_by=?,published_at=COALESCE(published_at,now())
            WHERE theme_id=? AND theme_version_id=? AND status IN ('draft','published','archived')
            """, userId(), themeId, versionId);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_theme SET current_version_id=?,updated_by=?,update_time=now()
            WHERE theme_id=?
            """, versionId, userId(), themeId);
        knowledgeJdbcTemplate.update("""
            INSERT INTO knowledge_portal_theme_usage(site_id,config_version_id,theme_id,theme_version_id)
            VALUES (?,?,?,?)
            """, siteId, configVersionId, themeId, versionId);
    }

    private Long clonePublishedTheme(Map<String, Object> theme, Long siteId) {
        long themeId = ((Number) theme.get("themeId")).longValue();
        long sourceId = ((Number) theme.get("currentVersionId")).longValue();
        Integer next = knowledgeJdbcTemplate.queryForObject(
            "SELECT COALESCE(max(version_no),0)+1 FROM knowledge_portal_theme_version WHERE theme_id=?",
            Integer.class, themeId);
        Map<String, Object> source = knowledgeJdbcTemplate.queryForMap("""
            SELECT manifest_json::text AS "manifestJson",checksum,
                   file_count AS "fileCount",expanded_size AS "expandedSize"
            FROM knowledge_portal_theme_version WHERE theme_version_id=?
            """, sourceId);
        Long id = knowledgeJdbcTemplate.queryForObject("""
            INSERT INTO knowledge_portal_theme_version
                (theme_id,version_no,status,manifest_json,compiled_json,checksum,scan_status,scan_result,
                 file_count,expanded_size,created_by)
            VALUES (?,?,'draft',?::jsonb,'{}'::jsonb,?,'passed','{"issues":[]}'::jsonb,?,?,?)
            RETURNING theme_version_id
            """, Long.class, themeId, next, source.get("manifestJson"), source.get("checksum"),
            source.get("fileCount"), source.get("expandedSize"), userId());
        knowledgeJdbcTemplate.update("""
            INSERT INTO knowledge_portal_theme_file
                (theme_version_id,file_path,mime_type,size_bytes,checksum,content)
            SELECT ?,file_path,mime_type,size_bytes,checksum,content
            FROM knowledge_portal_theme_file WHERE theme_version_id=?
            """, id, sourceId);
        return id;
    }

    private Long ensurePortalDraft(Long siteId, Long themeVersionId) {
        List<Long> existing = knowledgeJdbcTemplate.queryForList("""
            SELECT config_version_id FROM knowledge_portal_config_version
            WHERE site_id=? AND status='draft' AND schema_version=4
              AND (config_json->'theme'->>'versionId')::bigint=?
            ORDER BY version_no DESC LIMIT 1
            """, Long.class, siteId, themeVersionId);
        if (!existing.isEmpty()) return existing.getFirst();
        String source = knowledgeJdbcTemplate.queryForObject("""
            SELECT config_json::text FROM knowledge_portal_config_version
            WHERE site_id=? ORDER BY CASE status WHEN 'published' THEN 0 ELSE 1 END,version_no DESC LIMIT 1
            """, String.class, siteId);
        JsonNode config;
        try {
            config = objectMapper.readTree(source);
        } catch (JsonProcessingException ex) {
            throw new KmaException(500, "PORTAL_CONFIG_CORRUPTED");
        }
        if (!(config instanceof ObjectNode root)) throw new KmaException(409, "PORTAL_THEME_V4_REQUIRED");
        if (root.path("schemaVersion").asInt() != 4) {
            Long themeId = knowledgeJdbcTemplate.queryForObject(
                "SELECT theme_id FROM knowledge_portal_theme_version WHERE theme_version_id=?",
                Long.class, themeVersionId);
            root = convertToV4(root, themeId, themeVersionId);
        }
        ((ObjectNode) root.path("theme")).put("versionId", themeVersionId);
        root.put("revision", "theme-draft-" + System.currentTimeMillis());
        Integer next = knowledgeJdbcTemplate.queryForObject("""
            SELECT COALESCE(max(version_no),0)+1 FROM knowledge_portal_config_version WHERE site_id=?
            """, Integer.class, siteId);
        return knowledgeJdbcTemplate.queryForObject("""
            INSERT INTO knowledge_portal_config_version
                (site_id,version_no,status,schema_version,config_json,checksum,change_note,created_by)
            VALUES (?,?,'draft',4,?::jsonb,?,'Portal Theme V4 工作区',?) RETURNING config_version_id
            """, Long.class, siteId, next, json(root), checksum(root), userId());
    }

    private Map<String, Object> workspaceView(Map<String, Object> site, Map<String, Object> theme,
                                               Long themeVersionId, Long portalVersionId) {
        Map<String, Object> version = knowledgeJdbcTemplate.queryForMap("""
            SELECT theme_version_id AS "themeVersionId",version_no AS "versionNo",status,
                   manifest_json::text AS "manifestJson",checksum,scan_status AS "scanStatus",
                   scan_result::text AS "scanResultJson",lock_version AS "lockVersion"
            FROM knowledge_portal_theme_version WHERE theme_version_id=?
            """, themeVersionId);
        Map<String, Object> portal = knowledgeJdbcTemplate.queryForMap("""
            SELECT config_version_id AS "versionId",version_no AS "versionNo",status,
                   schema_version AS "schemaVersion",lock_version AS "lockVersion",
                   config_json::text AS "configJson",
                   reviewed_at AS "reviewedAt",published_at AS "publishedAt"
            FROM knowledge_portal_config_version WHERE config_version_id=?
            """, portalVersionId);
        version.put("manifest", parseJson(version.remove("manifestJson")));
        version.put("scanResult", parseJson(version.remove("scanResultJson")));
        portal.put("config", parseJson(portal.remove("configJson")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("site", site);
        result.put("theme", theme);
        result.put("version", version);
        result.put("portalVersion", portal);
        result.put("files", files(themeVersionId));
        return result;
    }

    private Map<String, String> files(Long themeVersionId) {
        Map<String, String> result = new LinkedHashMap<>();
        knowledgeJdbcTemplate.query("""
            SELECT file_path,content FROM knowledge_portal_theme_file
            WHERE theme_version_id=? ORDER BY file_path
            """, rs -> {
            result.put(rs.getString(1), new String(rs.getBytes(2), StandardCharsets.UTF_8));
        }, themeVersionId);
        return result;
    }

    private void insertFile(Long versionId, String rawPath, String source) {
        String path = PortalThemeSecurity.normalizePath(rawPath);
        byte[] content = source.getBytes(StandardCharsets.UTF_8);
        knowledgeJdbcTemplate.update("""
            INSERT INTO knowledge_portal_theme_file
                (theme_version_id,file_path,mime_type,size_bytes,checksum,content)
            VALUES (?,?,?,?,?,?)
            """, versionId, path, mime(path), content.length, sha256(content), content);
    }

    private void requireOwnedVersion(String siteKey, Long versionId) {
        Integer count = knowledgeJdbcTemplate.queryForObject("""
            SELECT count(*) FROM knowledge_portal_theme_version v
            JOIN knowledge_portal_theme t ON t.theme_id=v.theme_id
            JOIN knowledge_portal_site s ON s.site_id=t.site_id
            WHERE s.site_key=? AND v.theme_version_id=?
            """, Integer.class, siteKey, versionId);
        if (count == null || count == 0) throw new KmaException(404, "PORTAL_THEME_VERSION_NOT_FOUND");
    }

    private void writeZip(ZipOutputStream zip, String path, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }

    private byte[] exportContent(String source) {
        if (source != null && source.startsWith("data:") && source.contains(";base64,")) {
            try {
                return Base64.getDecoder().decode(source.substring(source.indexOf(";base64,") + 8));
            } catch (IllegalArgumentException ignored) {
                // Preserve malformed input for diagnostics; security validation prevents publication.
            }
        }
        return value(source).getBytes(StandardCharsets.UTF_8);
    }

    private String importContent(String path, byte[] content) {
        String mediaType = mime(path);
        if (mediaType.startsWith("text/") || path.endsWith(".js") || path.endsWith(".json"))
            return new String(content, StandardCharsets.UTF_8);
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(content);
    }

    private String value(String source) {
        return source == null ? "" : source;
    }

    private Map<String, Object> requireSite(String siteKey) {
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT site_id AS "siteId",site_key AS "siteKey",name,scenario,status,
                   current_published_version_id AS "publishedVersionId"
            FROM knowledge_portal_site WHERE site_key=?
            """, siteKey);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_SITE_NOT_FOUND");
        return rows.getFirst();
    }

    private Map<String, Object> requireTheme(Long siteId) {
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT theme_id AS "themeId",site_id AS "siteId",theme_key AS "themeKey",
                   display_name AS "displayName",description,status,current_version_id AS "currentVersionId"
            FROM knowledge_portal_theme WHERE site_id=?
            """, siteId);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_THEME_NOT_FOUND");
        return rows.getFirst();
    }

    private Map<String, Object> ensureTheme(Map<String, Object> site) {
        Long siteId = ((Number) site.get("siteId")).longValue();
        List<Map<String, Object>> existing = knowledgeJdbcTemplate.queryForList("""
            SELECT theme_id AS "themeId",site_id AS "siteId",theme_key AS "themeKey",
                   display_name AS "displayName",description,status,current_version_id AS "currentVersionId"
            FROM knowledge_portal_theme WHERE site_id=?
            """, siteId);
        if (!existing.isEmpty()) return existing.getFirst();
        String themeKey = site.get("siteKey") + "-theme";
        Long themeId = knowledgeJdbcTemplate.queryForObject("""
            INSERT INTO knowledge_portal_theme
                (site_id,theme_key,display_name,description,created_by,updated_by)
            VALUES (?,?,?,'Portal Theme V4 全站主题',?,?) RETURNING theme_id
            """, Long.class, siteId, themeKey, site.get("name") + " 全站主题", userId(), userId());
        Map<String, String> defaults = defaultFiles();
        Long versionId = knowledgeJdbcTemplate.queryForObject("""
            INSERT INTO knowledge_portal_theme_version
                (theme_id,version_no,status,manifest_json,compiled_json,checksum,scan_status,scan_result,
                 file_count,expanded_size,created_by,published_by,scanned_at,published_at)
            VALUES (?,1,'published',?::jsonb,'{"compiler":"kma-liquid-v1","validated":true}'::jsonb,
                    ?,'passed','{"issues":[]}'::jsonb,?,?,?, ?,now(),now())
            RETURNING theme_version_id
            """, Long.class, themeId, json(Map.of(
                "kind", "portal-theme", "entry", "layout.html", "capabilities",
                List.of("page-context", "contents", "search", "ask", "analytics", "navigation"))),
            checksum(defaults), defaults.size(), defaults.values().stream()
                .mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length).sum(),
            userId(), userId());
        defaults.forEach((path, source) -> insertFile(versionId, path, source));
        knowledgeJdbcTemplate.update(
            "UPDATE knowledge_portal_theme SET current_version_id=? WHERE theme_id=?", versionId, themeId);
        return requireTheme(siteId);
    }

    private ObjectNode convertToV4(ObjectNode source, Long themeId, Long themeVersionId) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("schemaVersion", 4);
        result.put("revision", "theme-v4-" + System.currentTimeMillis());
        result.set("site", source.path("site").deepCopy());
        result.set("contentScope", source.path("contentScope").deepCopy());
        result.set("modules", source.path("modules").deepCopy());
        result.set("search", source.path("search").deepCopy());
        result.set("assistant", source.path("assistant").deepCopy());
        ObjectNode theme = result.putObject("theme");
        theme.put("themeId", themeId);
        theme.put("versionId", themeVersionId);
        ObjectNode routes = result.putObject("routes");
        for (String route : List.of("home", "library", "topics", "ask", "content", "search", "favorites", "profile"))
            routes.put(route, "pages/" + route + ".html");
        return result;
    }

    private Map<String, String> defaultFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("layout.html", """
            <div class="kma-site"><header><kma-link to="home">{{ site.name }}</kma-link>
            <nav><kma-link to="library">资料中心</kma-link><kma-link to="topics">专题</kma-link>
            <kma-link to="ask">AI 问答</kma-link></nav></header><main><kma-slot name="content" /></main>
            <footer>Portal Theme V4</footer></div>
            """);
        Map<String, String> widgets = Map.of(
            "home", "content-list", "library", "content-list", "topics", "topic-directory",
            "ask", "ai-chat", "content", "document-reader", "search", "content-list",
            "favorites", "favorite-list", "profile", "profile-card");
        widgets.forEach((route, widget) -> files.put(
            "pages/" + route + ".html",
            "<section><h1>{{ page.title }}</h1><kma-widget name=\"" + widget + "\" /></section>"));
        files.put("styles/theme.css", """
            :root{font-family:system-ui,sans-serif;color:#17332d;background:#f5f8f7}
            *{box-sizing:border-box}body{margin:0}header,footer{padding:20px 5vw;background:#fff}
            header{display:flex;justify-content:space-between}nav{display:flex;gap:20px}main{padding:5vw}
            .kma-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}
            .kma-card{padding:20px;border:1px solid #dce7e3;border-radius:14px;background:#fff}
            @media(width<800px){.kma-grid{grid-template-columns:1fr}}
            """);
        files.put("scripts/theme.js", "document.documentElement.dataset.themeReady='true'");
        return files;
    }

    private String mime(String path) {
        if (path.endsWith(".html")) return "text/html;charset=UTF-8";
        if (path.endsWith(".css")) return "text/css;charset=UTF-8";
        if (path.endsWith(".js")) return "text/javascript;charset=UTF-8";
        if (path.endsWith(".json")) return "application/json;charset=UTF-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    private String checksum(Map<String, String> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new KmaException(500, "PORTAL_THEME_CHECKSUM_FAILED");
        }
    }

    private String checksum(JsonNode value) {
        return sha256(json(value).getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new KmaException(500, "PORTAL_THEME_CHECKSUM_FAILED");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new KmaException(400, "PORTAL_THEME_MANIFEST_INVALID");
        }
    }

    private JsonNode parseJson(Object value) {
        if (value instanceof JsonNode node) return node;
        try {
            return objectMapper.readTree(String.valueOf(value));
        } catch (JsonProcessingException ex) {
            throw new KmaException(500, "PORTAL_THEME_JSON_CORRUPTED");
        }
    }

    private long userId() {
        Long value = KmaIdentityContext.getUserId();
        return value == null ? 0L : value;
    }
}
