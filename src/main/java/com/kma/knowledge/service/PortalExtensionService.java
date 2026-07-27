package com.kma.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.PortalExtensionReleaseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry and publish-time guard for platform-signed, sandboxed portal extension packages. */
@Service
@RequiredArgsConstructor
public class PortalExtensionService {
    private static final Set<String> SLOTS = Set.of("header", "main", "sidebar", "footer");
    private static final Set<String> CAPABILITIES = Set.of("page-context", "contents", "search", "ask", "analytics", "assets");

    private final JdbcTemplate knowledgeJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityAuditService auditService;

    @Value("${kma.portal-extension.signing-key:}")
    private String signingKey;

    public List<Map<String, Object>> catalog() {
        return knowledgeJdbcTemplate.queryForList("""
            SELECT extension_id AS "extensionId",extension_version AS "version",display_name AS "displayName",
                   entry_url AS "entryUrl",integrity_hash AS "integrityHash",manifest_json AS manifest,
                   min_frontend_version AS "minFrontendVersion",status,create_time AS "createTime"
            FROM portal_extension_release WHERE status='active'
            ORDER BY extension_id,extension_version DESC
            """);
    }

    public List<String> validateReferences(JsonNode config) {
        List<String> issues = new ArrayList<>();
        JsonNode pages = config.path("pages");
        if (!pages.isObject()) return issues;
        if (config.path("schemaVersion").asInt() == 3) {
            Set<String> platformPackages = packageKeys(config, "platform");
            pages.fields().forEachRemaining(page ->
                collectSandboxNodes(page.getValue().path("root")).stream()
                    .filter(node -> platformPackages.contains(node.path("packageId").asText()))
                    .forEach(node -> validatePlatformSandbox(page.getKey(), node, issues)));
            return issues;
        }
        pages.fields().forEachRemaining(page -> {
            JsonNode extensions = page.getValue().path("extensions");
            if (extensions.isMissingNode()) return;
            if (!extensions.isArray()) {
                issues.add("页面 " + page.getKey() + " 的 extensions 必须为数组");
                return;
            }
            for (JsonNode binding : extensions) validateBinding(page.getKey(), binding, issues);
        });
        return issues;
    }

    /** Resolves only the active bindings of a published page; no catalog-wide data is exposed to portal users. */
    public List<Map<String, Object>> resolveBindings(JsonNode page) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (page.path("root").isObject()) {
            for (JsonNode node : collectSandboxNodes(page.path("root"))) {
                try {
                    Map<String, Object> release = new LinkedHashMap<>(lookup(
                        node.path("packageId").asText(), node.path("version").asText()));
                    release.put("slotKey", node.path("id").asText());
                    release.put("region", "main");
                    release.put("config", node(node.path("config")));
                    result.add(release);
                } catch (KmaException ignored) {
                    // Site packages are resolved by PortalCodePackageService.
                }
            }
            return List.copyOf(result);
        }
        JsonNode bindings = page.path("extensions");
        if (!bindings.isArray()) return result;
        for (JsonNode binding : bindings) {
            if (!binding.path("enabled").asBoolean(true)) continue;
            try {
                Map<String, Object> release = new LinkedHashMap<>(lookup(
                    binding.path("extensionId").asText(), binding.path("version").asText()));
                release.put("slotKey", binding.path("slotKey").asText());
                release.put("region", binding.path("region").asText("main"));
                release.put("config", node(binding.path("config")));
                result.add(release);
            } catch (KmaException ignored) {
                // A release may be retired after publishing. The host renders a local fallback instead of failing a page.
            }
        }
        return List.copyOf(result);
    }

    public void compileUsage(Long siteId, Long versionId, JsonNode config) {
        knowledgeJdbcTemplate.update("""
            DELETE FROM knowledge_portal_extension_usage
            WHERE site_id=? AND config_version_id=?
            """, siteId, versionId);
        JsonNode pages = config.path("pages");
        if (!pages.isObject()) return;
        if (config.path("schemaVersion").asInt() == 3) {
            Set<String> platformPackages = packageKeys(config, "platform");
            pages.fields().forEachRemaining(page -> {
                for (JsonNode node : collectSandboxNodes(page.getValue().path("root"))) {
                    if (!platformPackages.contains(node.path("packageId").asText())) continue;
                    knowledgeJdbcTemplate.update("""
                        INSERT INTO knowledge_portal_extension_usage
                            (site_id,config_version_id,page_slug,region,extension_id,extension_version,slot_key,extension_config)
                        VALUES (?,?,?,?,?,?,?,?::jsonb)
                        """, siteId, versionId, page.getKey(), "main",
                        node.path("packageId").asText(), node.path("version").asText(),
                        node.path("id").asText(), json(node.path("config")));
                }
            });
            return;
        }
        pages.fields().forEachRemaining(page -> {
            JsonNode extensions = page.getValue().path("extensions");
            if (!extensions.isArray()) return;
            for (JsonNode binding : extensions) {
                if (!binding.path("enabled").asBoolean(true)) continue;
                knowledgeJdbcTemplate.update("""
                    INSERT INTO knowledge_portal_extension_usage
                        (site_id,config_version_id,page_slug,region,extension_id,extension_version,slot_key,extension_config)
                    VALUES (?,?,?,?,?,?,?,?::jsonb)
                    """, siteId, versionId, page.getKey(), binding.path("region").asText("main"),
                    binding.path("extensionId").asText(), binding.path("version").asText(),
                    binding.path("slotKey").asText(), json(binding.path("config")));
            }
        });
    }

    public Map<String, Object> release(PortalExtensionReleaseRequest request) {
        validateManifest(request);
        verifySignature(request);
        try {
            knowledgeJdbcTemplate.update("""
                INSERT INTO portal_extension_release
                    (extension_id,extension_version,display_name,entry_url,integrity_hash,manifest_json,signature,status,min_frontend_version,created_by)
                VALUES (?,?,?,?,?,?::jsonb,?,'active',?,?)
                """, request.getExtensionId(), request.getVersion(), request.getDisplayName(), request.getEntryUrl(),
                request.getIntegrityHash(), json(request.getManifest()), request.getSignature(), request.getMinFrontendVersion(),
                KmaIdentityContext.getUserId());
        } catch (DuplicateKeyException ex) {
            throw new KmaException(409, "PORTAL_EXTENSION_RELEASE_EXISTS");
        }
        auditService.recordRequired("portal_extension", "info", "portal-extension.release",
            request.getExtensionId() + "@" + request.getVersion(), Map.of(), Map.of(
                "entryUrl", request.getEntryUrl(), "integrityHash", request.getIntegrityHash()), Map.of());
        return lookup(request.getExtensionId(), request.getVersion());
    }

    private void validateBinding(String page, JsonNode binding, List<String> issues) {
        if (!binding.isObject()) {
            issues.add("页面 " + page + " 包含非对象扩展绑定");
            return;
        }
        String extensionId = binding.path("extensionId").asText();
        String version = binding.path("version").asText();
        String slot = binding.path("slotKey").asText();
        String region = binding.path("region").asText("main");
        if (!extensionId.matches("^[a-z][a-z0-9_-]{1,63}$") || version.isBlank() || slot.isBlank()) {
            issues.add("页面 " + page + " 的扩展标识、版本或槽位不合法");
            return;
        }
        if (!SLOTS.contains(region)) {
            issues.add("页面 " + page + " 的扩展区域不受支持");
            return;
        }
        Map<String, Object> release;
        try {
            release = lookup(extensionId, version);
        } catch (KmaException ex) {
            issues.add("扩展不可用: " + extensionId + "@" + version);
            return;
        }
        JsonNode manifest = node(release.get("manifest"));
        if (!manifest.path("slots").isArray() || !contains(manifest.path("slots"), region)) {
            issues.add("扩展 " + extensionId + " 不支持区域 " + region);
        }
        JsonNode extensionConfig = binding.path("config");
        if (!extensionConfig.isMissingNode() && !extensionConfig.isObject())
            issues.add("扩展 " + extensionId + " 的 config 必须为对象");
        if (extensionConfig.isObject() && extensionConfig.size() > 30)
            issues.add("扩展 " + extensionId + " 的 config 字段过多");
        if (extensionConfig.isObject()) validateSettings(extensionId, extensionConfig, manifest.path("settingsSchema"), issues);
    }

    private void validatePlatformSandbox(String page, JsonNode node, List<String> issues) {
        String extensionId = node.path("packageId").asText();
        String version = node.path("version").asText();
        try {
            Map<String, Object> release = lookup(extensionId, version);
            JsonNode manifest = node(release.get("manifest"));
            JsonNode config = node.path("config");
            if (config.isObject()) validateSettings(extensionId, config, manifest.path("settingsSchema"), issues);
        } catch (KmaException ex) {
            issues.add("页面 " + page + " 的平台扩展不可用: " + extensionId + "@" + version);
        }
    }

    private List<JsonNode> collectSandboxNodes(JsonNode root) {
        List<JsonNode> result = new ArrayList<>();
        collectSandboxNodes(root, result, 0);
        return result;
    }

    private void collectSandboxNodes(JsonNode node, List<JsonNode> result, int depth) {
        if (!node.isObject() || depth > 8) return;
        if ("sandbox".equals(node.path("type").asText())) result.add(node);
        if (node.path("children").isArray())
            for (JsonNode child : node.path("children")) collectSandboxNodes(child, result, depth + 1);
    }

    private Set<String> packageKeys(JsonNode config, String source) {
        Set<String> result = new java.util.LinkedHashSet<>();
        if (config.path("packages").isArray()) for (JsonNode item : config.path("packages"))
            if (source.equals(item.path("source").asText())) result.add(item.path("packageId").asText());
        return result;
    }

    private Map<String, Object> lookup(String extensionId, String version) {
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT extension_id AS "extensionId",extension_version AS "version",display_name AS "displayName",
                   entry_url AS "entryUrl",integrity_hash AS "integrityHash",manifest_json AS manifest,
                   min_frontend_version AS "minFrontendVersion",status
            FROM portal_extension_release
            WHERE extension_id=? AND extension_version=? AND status='active'
            """, extensionId, version);
        if (rows.isEmpty()) throw new KmaException(400, "PORTAL_EXTENSION_NOT_AVAILABLE");
        return rows.getFirst();
    }

    private void validateManifest(PortalExtensionReleaseRequest request) {
        JsonNode manifest = request.getManifest();
        if (!manifest.isObject() || !request.getExtensionId().equals(manifest.path("id").asText())
            || !request.getVersion().equals(manifest.path("version").asText()))
            throw new KmaException(400, "PORTAL_EXTENSION_MANIFEST_INVALID");
        JsonNode slots = manifest.path("slots");
        if (!slots.isArray() || slots.isEmpty() || slots.size() > 4 || containsUnsupported(slots, SLOTS))
            throw new KmaException(400, "PORTAL_EXTENSION_SLOTS_INVALID");
        JsonNode capabilities = manifest.path("capabilities");
        if (capabilities.isArray() && containsUnsupported(capabilities, CAPABILITIES))
            throw new KmaException(400, "PORTAL_EXTENSION_CAPABILITY_INVALID");
        if (!request.getEntryUrl().equals("/portal-extensions/" + request.getExtensionId() + "/" + request.getVersion() + "/index.html"))
            throw new KmaException(400, "PORTAL_EXTENSION_ENTRY_INVALID");
        JsonNode settings = manifest.path("settingsSchema");
        if (!settings.isMissingNode() && (!settings.isObject() || !settings.path("properties").isObject()))
            throw new KmaException(400, "PORTAL_EXTENSION_SETTINGS_SCHEMA_INVALID");
    }

    private void verifySignature(PortalExtensionReleaseRequest request) {
        if (!StringUtils.hasText(signingKey)) throw new KmaException(503, "PORTAL_EXTENSION_SIGNING_NOT_CONFIGURED");
        String expected = hmac(request.getExtensionId() + "|" + request.getVersion() + "|" + request.getEntryUrl()
            + "|" + request.getIntegrityHash() + "|" + json(request.getManifest()));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), request.getSignature().getBytes(StandardCharsets.UTF_8)))
            throw new KmaException(403, "PORTAL_EXTENSION_SIGNATURE_INVALID");
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new KmaException(500, "PORTAL_EXTENSION_SIGNATURE_FAILED");
        }
    }

    private boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) if (expected.equals(value.asText())) return true;
        return false;
    }

    private boolean containsUnsupported(JsonNode values, Set<String> allowed) {
        for (JsonNode value : values) if (!allowed.contains(value.asText())) return true;
        return false;
    }

    /** Small, deliberately bounded settings schema rather than accepting arbitrary JSON Schema at runtime. */
    private void validateSettings(String extensionId, JsonNode config, JsonNode schema, List<String> issues) {
        if (schema.isMissingNode()) {
            if (!config.isEmpty()) issues.add("扩展 " + extensionId + " 未声明可配置项");
            return;
        }
        JsonNode properties = schema.path("properties");
        config.fields().forEachRemaining(entry -> {
            JsonNode definition = properties.path(entry.getKey());
            if (definition.isMissingNode()) {
                issues.add("扩展 " + extensionId + " 包含未声明配置: " + entry.getKey());
                return;
            }
            String type = definition.path("type").asText();
            JsonNode value = entry.getValue();
            boolean typeValid = switch (type) {
                case "string" -> value.isTextual();
                case "boolean" -> value.isBoolean();
                case "integer" -> value.isIntegralNumber();
                case "number" -> value.isNumber();
                default -> false;
            };
            if (!typeValid) {
                issues.add("扩展 " + extensionId + " 的配置 " + entry.getKey() + " 类型不合法");
                return;
            }
            if (value.isTextual() && value.textValue().length() > definition.path("maxLength").asInt(500))
                issues.add("扩展 " + extensionId + " 的配置 " + entry.getKey() + " 长度超限");
            if (value.isNumber() && definition.has("minimum") && value.decimalValue().compareTo(definition.path("minimum").decimalValue()) < 0)
                issues.add("扩展 " + extensionId + " 的配置 " + entry.getKey() + " 小于最小值");
            if (value.isNumber() && definition.has("maximum") && value.decimalValue().compareTo(definition.path("maximum").decimalValue()) > 0)
                issues.add("扩展 " + extensionId + " 的配置 " + entry.getKey() + " 超过最大值");
        });
    }

    private JsonNode node(Object value) {
        if (value instanceof JsonNode jsonNode) return jsonNode;
        try {
            return objectMapper.readTree(String.valueOf(value));
        } catch (JsonProcessingException ex) {
            throw new KmaException(500, "PORTAL_EXTENSION_MANIFEST_CORRUPTED");
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new KmaException(400, "PORTAL_EXTENSION_JSON_INVALID");
        }
    }
}
