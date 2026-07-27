package com.kma.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.PortalCodeFilesRequest;
import com.kma.knowledge.dto.PortalCodePackageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Site-owned static portal components. Source is stored, scanned and served only from immutable versions. */
@Service
@RequiredArgsConstructor
public class PortalCodePackageService {
    private static final long MAX_ZIP_BYTES = 2L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 10L * 1024 * 1024;
    private static final int MAX_FILES = 50;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "html", "css", "js", "json", "svg", "png", "jpg", "jpeg", "gif", "webp", "woff2");
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
        Map.entry("html", "text/html;charset=UTF-8"),
        Map.entry("css", "text/css;charset=UTF-8"),
        Map.entry("js", "text/javascript;charset=UTF-8"),
        Map.entry("json", "application/json;charset=UTF-8"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("png", "image/png"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"),
        Map.entry("webp", "image/webp"),
        Map.entry("woff2", "font/woff2"));
    private static final List<String> SCRIPT_DENYLIST = List.of(
        "fetch(", "xmlhttprequest", "websocket", "eventsource", "sendbeacon",
        "document.cookie", "localstorage", "sessionstorage", "window.parent", "window.top",
        "window.opener", "window.open(", "eval(", "new function(");

    private final JdbcTemplate knowledgeJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityAuditService auditService;

    @Value("${kma.portal-sandbox-origin:}")
    private String sandboxOrigin;

    public List<Map<String, Object>> list() {
        return knowledgeJdbcTemplate.queryForList("""
            SELECT p.package_id AS "packageId",p.package_key AS "packageKey",p.display_name AS "displayName",
                   p.description,p.status,p.current_version_id AS "currentVersionId",p.update_time AS "updateTime",
                   v.version_label AS "currentVersion",v.scan_status AS "scanStatus"
            FROM knowledge_portal_code_package p
            LEFT JOIN knowledge_portal_code_version v
              ON v.code_version_id=p.current_version_id
            ORDER BY p.update_time DESC,p.package_id DESC
            """);
    }

    public Map<String, Object> get(Long packageId) {
        Map<String, Object> result = new LinkedHashMap<>(requirePackage(packageId));
        result.put("versions", knowledgeJdbcTemplate.queryForList("""
            SELECT code_version_id AS "versionId",version_no AS "versionNo",version_label AS version,status,
                   source_mode AS "sourceMode",entry_path AS "entryPath",manifest_json AS manifest,
                   checksum,scan_status AS "scanStatus",scan_result AS "scanResult",file_count AS "fileCount",
                   compressed_size AS "compressedSize",expanded_size AS "expandedSize",
                   create_time AS "createTime",scanned_at AS "scannedAt",published_at AS "publishedAt"
            FROM knowledge_portal_code_version
            WHERE package_id=? ORDER BY version_no DESC
            """, packageId));
        return result;
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> create(PortalCodePackageRequest request) {
        try {
            Long id = knowledgeJdbcTemplate.queryForObject("""
                INSERT INTO knowledge_portal_code_package
                    (package_key,display_name,description,created_by,updated_by)
                VALUES (?,?,?,?,?) RETURNING package_id
                """, Long.class, request.getPackageKey(), request.getDisplayName(),
                request.getDescription(), KmaIdentityContext.getUserId(), KmaIdentityContext.getUserId());
            auditService.recordRequired("portal_code", "info", "portal-code.create",
                "portal-code:" + request.getPackageKey(), Map.of(), Map.of(
                    "displayName", request.getDisplayName()), Map.of());
            return get(id);
        } catch (DuplicateKeyException ex) {
            throw new KmaException(409, "PORTAL_CODE_PACKAGE_EXISTS");
        }
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> update(Long packageId, PortalCodePackageRequest request) {
        Map<String, Object> before = requirePackage(packageId);
        int changed = knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_code_package
            SET display_name=?,description=?,updated_by=?,update_time=now()
            WHERE package_id=?
            """, request.getDisplayName(), request.getDescription(), KmaIdentityContext.getUserId(), packageId);
        if (changed == 0) throw new KmaException(404, "PORTAL_CODE_PACKAGE_NOT_FOUND");
        auditService.recordRequired("portal_code", "info", "portal-code.update",
            "portal-code:" + before.get("packageKey"), Map.of("displayName", before.get("displayName")),
            Map.of("displayName", request.getDisplayName()), Map.of());
        return get(packageId);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> saveEditorFiles(Long packageId, PortalCodeFilesRequest request) {
        requirePackage(packageId);
        if (request.getFiles() == null || request.getFiles().isEmpty())
            throw new KmaException(400, "PORTAL_CODE_FILES_REQUIRED");
        List<StaticFile> files = new ArrayList<>();
        request.getFiles().forEach((path, content) ->
            files.add(validateFile(path, content.getBytes(StandardCharsets.UTF_8))));
        return createVersion(packageId, request.getVersion(), "editor", request.getManifest(), files, 0);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> importZip(Long packageId, String version, JsonNode manifest, MultipartFile file) {
        requirePackage(packageId);
        if (file == null || file.isEmpty() || file.getSize() > MAX_ZIP_BYTES)
            throw new KmaException(400, "PORTAL_CODE_ZIP_SIZE_INVALID");
        return createVersion(packageId, version, "zip", manifest, readZip(file), file.getSize());
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> scan(Long packageId, Long versionId) {
        requireVersion(packageId, versionId, "draft");
        List<Map<String, Object>> files = knowledgeJdbcTemplate.queryForList("""
            SELECT file_path AS "filePath",mime_type AS "mimeType",content
            FROM knowledge_portal_code_file WHERE code_version_id=?
            """, versionId);
        List<String> issues = scanFiles(files);
        String status = issues.isEmpty() ? "passed" : "failed";
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_code_version
            SET scan_status=?,scan_result=?::jsonb,scanned_at=now()
            WHERE package_id=? AND code_version_id=? AND status='draft'
            """, status, json(Map.of("issues", issues)), packageId, versionId);
        auditService.recordRequired("portal_code", issues.isEmpty() ? "info" : "warning", "portal-code.scan",
            "portal-code-version:" + versionId, Map.of(), Map.of("status", status, "issues", issues), Map.of());
        return version(packageId, versionId);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> publish(Long packageId, Long versionId) {
        Map<String, Object> candidate = requireVersion(packageId, versionId, "draft");
        if (!"passed".equals(candidate.get("scanStatus")))
            throw new KmaException(409, "PORTAL_CODE_SCAN_REQUIRED");
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_code_version
            SET status='published',published_by=?,published_at=now()
            WHERE package_id=? AND code_version_id=? AND status='draft'
            """, KmaIdentityContext.getUserId(), packageId, versionId);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_code_package
            SET status='active',current_version_id=?,updated_by=?,update_time=now()
            WHERE package_id=?
            """, versionId, KmaIdentityContext.getUserId(), packageId);
        auditService.recordRequired("portal_code", "info", "portal-code.publish",
            "portal-code-version:" + versionId, Map.of(), Map.of("checksum", candidate.get("checksum")), Map.of());
        return get(packageId);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void revoke(Long packageId, Long versionId) {
        requireVersion(packageId, versionId, "published");
        Integer usages = knowledgeJdbcTemplate.queryForObject("""
            SELECT count(*) FROM knowledge_portal_code_usage
            WHERE package_id=? AND code_version_id=?
            """, Integer.class, packageId, versionId);
        if (usages != null && usages > 0) throw new KmaException(409, "PORTAL_CODE_VERSION_IN_USE");
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_code_version SET status='revoked',revoked_at=now()
            WHERE package_id=? AND code_version_id=?
            """, packageId, versionId);
        knowledgeJdbcTemplate.update("""
            UPDATE knowledge_portal_code_package SET status='revoked',current_version_id=NULL,update_time=now()
            WHERE package_id=? AND current_version_id=?
            """, packageId, versionId);
        auditService.recordRequired("portal_code", "warning", "portal-code.revoke",
            "portal-code-version:" + versionId, Map.of(), Map.of("revoked", true), Map.of());
    }

    public StaticResource publishedFile(String packageKey, String version, String path) {
        String safePath = safePath(path);
        List<StaticResource> rows = knowledgeJdbcTemplate.query("""
            SELECT f.mime_type,f.content
            FROM knowledge_portal_code_package p
            JOIN knowledge_portal_code_version v
              ON v.package_id=p.package_id
            JOIN knowledge_portal_code_file f
              ON f.code_version_id=v.code_version_id
            WHERE p.package_key=? AND v.version_label=? AND v.status='published'
              AND f.file_path=?
            """, (rs, rowNum) -> new StaticResource(rs.getString(1), rs.getBytes(2)),
            packageKey, version, safePath);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_CODE_FILE_NOT_FOUND");
        return rows.getFirst();
    }

    public List<String> validateReferences(JsonNode config) {
        List<String> issues = new ArrayList<>();
        if (config.path("schemaVersion").asInt() != 3) return issues;
        Set<String> sitePackages = packageKeys(config, "site");
        JsonNode pages = config.path("pages");
        if (!pages.isObject()) return issues;
        pages.fields().forEachRemaining(page ->
            collectSandboxNodes(page.getValue().path("root")).forEach(node -> {
                String packageKey = node.path("packageId").asText();
                String version = node.path("version").asText();
                if (!sitePackages.contains(packageKey)) return;
                Integer count = knowledgeJdbcTemplate.queryForObject("""
                    SELECT count(*) FROM knowledge_portal_code_package p
                    JOIN knowledge_portal_code_version v
                      ON v.package_id=p.package_id
                    WHERE p.package_key=? AND v.version_label=? AND v.status='published'
                    """, Integer.class, packageKey, version);
                if (count == null || count == 0)
                    issues.add("页面 " + page.getKey() + " 的站点沙箱组件不可用: " + packageKey + "@" + version);
            }));
        return List.copyOf(issues);
    }

    public List<Map<String, Object>> resolveBindings(JsonNode page) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode node : collectSandboxNodes(page.path("root"))) {
            String packageKey = node.path("packageId").asText();
            String version = node.path("version").asText();
            List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
                SELECT p.display_name AS "displayName",v.manifest_json AS manifest,v.checksum
                FROM knowledge_portal_code_package p
                JOIN knowledge_portal_code_version v
                  ON v.package_id=p.package_id
                WHERE p.package_key=? AND v.version_label=? AND v.status='published'
                """, packageKey, version);
            if (rows.isEmpty()) continue;
            Map<String, Object> binding = new LinkedHashMap<>(rows.getFirst());
            binding.put("extensionId", packageKey);
            binding.put("version", version);
            binding.put("slotKey", node.path("id").asText());
            binding.put("region", "main");
            binding.put("config", node.path("config"));
            binding.put("integrityHash", "sha256-" + rows.getFirst().getOrDefault("checksum", ""));
            String prefix = StringUtils.hasText(sandboxOrigin) ? sandboxOrigin.replaceAll("/+$", "") : "";
            binding.put("entryUrl", prefix + "/portal-sandbox/" + packageKey + "/" + version + "/index.html");
            result.add(binding);
        }
        return List.copyOf(result);
    }

    public void compileUsage(Long siteId, Long configVersionId, JsonNode config) {
        knowledgeJdbcTemplate.update("""
            DELETE FROM knowledge_portal_code_usage
            WHERE site_id=? AND config_version_id=?
            """, siteId, configVersionId);
        if (config.path("schemaVersion").asInt() != 3) return;
        Set<String> sitePackages = packageKeys(config, "site");
        config.path("pages").fields().forEachRemaining(page -> {
            for (JsonNode node : collectSandboxNodes(page.getValue().path("root"))) {
                if (!sitePackages.contains(node.path("packageId").asText())) continue;
                List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
                    SELECT p.package_id AS "packageId",v.code_version_id AS "versionId"
                    FROM knowledge_portal_code_package p
                    JOIN knowledge_portal_code_version v
                      ON v.package_id=p.package_id
                    WHERE p.package_key=? AND v.version_label=? AND v.status='published'
                    """, node.path("packageId").asText(), node.path("version").asText());
                if (rows.isEmpty()) throw new KmaException(400, "PORTAL_CODE_VERSION_NOT_AVAILABLE");
                knowledgeJdbcTemplate.update("""
                    INSERT INTO knowledge_portal_code_usage
                        (site_id,config_version_id,page_slug,node_id,package_id,code_version_id)
                    VALUES (?,?,?,?,?,?)
                    """, siteId, configVersionId, page.getKey(), node.path("id").asText(),
                    rows.getFirst().get("packageId"), rows.getFirst().get("versionId"));
            }
        });
    }

    private List<JsonNode> collectSandboxNodes(JsonNode root) {
        List<JsonNode> result = new ArrayList<>();
        collectSandboxNodes(root, result, 0);
        return result;
    }

    private Set<String> packageKeys(JsonNode config, String source) {
        Set<String> result = new java.util.LinkedHashSet<>();
        JsonNode packages = config.path("packages");
        if (packages.isArray()) for (JsonNode item : packages)
            if (source.equals(item.path("source").asText())) result.add(item.path("packageId").asText());
        return result;
    }

    private void collectSandboxNodes(JsonNode node, List<JsonNode> result, int depth) {
        if (!node.isObject() || depth > 8) return;
        if ("sandbox".equals(node.path("type").asText())) result.add(node);
        JsonNode children = node.path("children");
        if (children.isArray()) for (JsonNode child : children) collectSandboxNodes(child, result, depth + 1);
    }

    private Map<String, Object> createVersion(Long packageId, String version, String sourceMode,
                                              JsonNode manifest, List<StaticFile> files, long compressedSize) {
        if (!StringUtils.hasText(version) || !version.matches("^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$"))
            throw new KmaException(400, "PORTAL_CODE_VERSION_INVALID");
        if (files.size() > MAX_FILES) throw new KmaException(400, "PORTAL_CODE_FILE_LIMIT");
        long expanded = files.stream().mapToLong(item -> item.content().length).sum();
        if (expanded > MAX_EXPANDED_BYTES) throw new KmaException(400, "PORTAL_CODE_EXPANDED_SIZE_INVALID");
        if (files.stream().noneMatch(item -> "index.html".equals(item.path())))
            throw new KmaException(400, "PORTAL_CODE_ENTRY_REQUIRED");
        int next = knowledgeJdbcTemplate.queryForObject("""
            SELECT COALESCE(max(version_no),0)+1 FROM knowledge_portal_code_version
            WHERE package_id=?
            """, Integer.class, packageId);
        String digest = checksum(files);
        Long id;
        try {
            id = knowledgeJdbcTemplate.queryForObject("""
                INSERT INTO knowledge_portal_code_version
                    (package_id,version_no,version_label,status,source_mode,entry_path,manifest_json,
                     checksum,scan_status,file_count,compressed_size,expanded_size,created_by)
                VALUES (?,?,?,'draft',?,'index.html',?::jsonb,?,'pending',?,?,?,?)
                RETURNING code_version_id
                """, Long.class, packageId, next, version, sourceMode,
                json(manifest == null ? Map.of() : manifest), digest, files.size(), compressedSize,
                expanded, KmaIdentityContext.getUserId());
        } catch (DuplicateKeyException ex) {
            throw new KmaException(409, "PORTAL_CODE_VERSION_EXISTS");
        }
        for (StaticFile file : files) knowledgeJdbcTemplate.update("""
            INSERT INTO knowledge_portal_code_file
                (code_version_id,file_path,mime_type,size_bytes,checksum,content)
            VALUES (?,?,?,?,?,?)
            """, id, file.path(), file.mime(), file.content().length,
            sha256(file.content()), file.content());
        return version(packageId, id);
    }

    private List<StaticFile> readZip(MultipartFile file) {
        List<StaticFile> result = new ArrayList<>();
        long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (result.size() >= MAX_FILES) throw new KmaException(400, "PORTAL_CODE_FILE_LIMIT");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES)
                        throw new KmaException(400, "PORTAL_CODE_EXPANDED_SIZE_INVALID");
                }
                result.add(validateFile(entry.getName(), output.toByteArray()));
            }
        } catch (IOException ex) {
            throw new KmaException(400, "PORTAL_CODE_ZIP_INVALID");
        }
        return result;
    }

    private StaticFile validateFile(String rawPath, byte[] content) {
        String path = safePath(rawPath);
        String extension = extension(path);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw new KmaException(400, "PORTAL_CODE_FILE_TYPE_FORBIDDEN");
        return new StaticFile(path, MIME_TYPES.get(extension), content);
    }

    private String safePath(String rawPath) {
        String path = rawPath == null ? "" : rawPath.replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.contains("../") || path.contains("/..")
            || path.contains("\0") || path.length() > 255)
            throw new KmaException(400, "PORTAL_CODE_FILE_PATH_INVALID");
        return path;
    }

    private String extension(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? "" : path.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private List<String> scanFiles(List<Map<String, Object>> files) {
        List<String> issues = new ArrayList<>();
        boolean entryFound = false;
        for (Map<String, Object> file : files) {
            String path = String.valueOf(file.get("filePath"));
            byte[] content = (byte[]) file.get("content");
            if ("index.html".equals(path)) entryFound = true;
            String ext = extension(path);
            if (!Set.of("html", "css", "js", "json", "svg").contains(ext)) continue;
            String text = new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if ("js".equals(ext)) {
                SCRIPT_DENYLIST.stream().filter(text::contains)
                    .forEach(token -> issues.add(path + " 包含禁止能力: " + token));
            }
            if ("html".equals(ext) && (text.contains("<form") || text.contains("<iframe")
                || text.matches("(?s).*<meta[^>]+http-equiv\\s*=\\s*['\"]?refresh.*")))
                issues.add(path + " 包含禁止的表单、iframe 或刷新跳转");
            if ("css".equals(ext) && (text.contains("@import") || text.matches("(?s).*url\\s*\\(\\s*['\"]?https?://.*")))
                issues.add(path + " 包含远程样式资源");
        }
        if (!entryFound) issues.add("缺少 index.html");
        return List.copyOf(issues);
    }

    private Map<String, Object> requirePackage(Long packageId) {
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT package_id AS "packageId",package_key AS "packageKey",display_name AS "displayName",
                   description,status,current_version_id AS "currentVersionId",create_time AS "createTime",
                   update_time AS "updateTime"
            FROM knowledge_portal_code_package WHERE package_id=?
            """, packageId);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_CODE_PACKAGE_NOT_FOUND");
        return rows.getFirst();
    }

    private Map<String, Object> requireVersion(Long packageId, Long versionId, String status) {
        Map<String, Object> result = version(packageId, versionId);
        if (!status.equals(result.get("status"))) throw new KmaException(409, "PORTAL_CODE_VERSION_STATE_INVALID");
        return result;
    }

    private Map<String, Object> version(Long packageId, Long versionId) {
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT code_version_id AS "versionId",version_no AS "versionNo",version_label AS version,status,
                   source_mode AS "sourceMode",entry_path AS "entryPath",manifest_json AS manifest,checksum,
                   scan_status AS "scanStatus",scan_result AS "scanResult",file_count AS "fileCount",
                   compressed_size AS "compressedSize",expanded_size AS "expandedSize"
            FROM knowledge_portal_code_version
            WHERE package_id=? AND code_version_id=?
            """, packageId, versionId);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_CODE_VERSION_NOT_FOUND");
        return rows.getFirst();
    }

    private String checksum(List<StaticFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.stream().sorted(java.util.Comparator.comparing(StaticFile::path)).forEach(file -> {
                digest.update(file.path().getBytes(StandardCharsets.UTF_8));
                digest.update(file.content());
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new KmaException(500, "PORTAL_CODE_CHECKSUM_FAILED");
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new KmaException(500, "PORTAL_CODE_CHECKSUM_FAILED");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new KmaException(400, "PORTAL_CODE_MANIFEST_INVALID");
        }
    }

    private record StaticFile(String path, String mime, byte[] content) {}

    public record StaticResource(String mimeType, byte[] content) {}
}
