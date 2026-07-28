package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kma.common.exception.KmaException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared, deliberately conservative security policy for portal sandbox source. */
final class PortalCodeSecurity {
    static final int MAX_INLINE_BYTES = 256 * 1024;
    static final int MAX_INLINE_FILES = 12;
    static final Set<String> CAPABILITIES = Set.of("page-context", "contents", "search", "ask", "analytics");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("html", "css", "js", "json", "svg");
    private static final List<String> SCRIPT_DENYLIST = List.of(
        "fetch(", "xmlhttprequest", "websocket", "eventsource", "sendbeacon",
        "document.cookie", "localstorage", "sessionstorage", "window.parent", "window.top",
        "window.opener", "window.open(", "eval(", "new function(", "import(");

    private PortalCodeSecurity() {}

    static List<String> validate(Map<String, String> files, JsonNode manifest, int maxFiles, long maxBytes) {
        List<String> issues = new ArrayList<>();
        if (files == null || files.isEmpty()) return List.of("代码文件不能为空");
        if (files.size() > maxFiles) issues.add("代码文件数量超过 " + maxFiles);
        long bytes = 0;
        boolean entryFound = false;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey() == null ? "" : entry.getKey().replace('\\', '/');
            String content = entry.getValue() == null ? "" : entry.getValue();
            bytes += content.getBytes(StandardCharsets.UTF_8).length;
            if (!safePath(path)) {
                issues.add("文件路径不合法: " + path);
                continue;
            }
            String extension = extension(path);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                issues.add(path + " 文件类型不受支持");
                continue;
            }
            if ("index.html".equals(path)) entryFound = true;
            scanText(path, extension, content, issues);
        }
        if (bytes > maxBytes) issues.add("代码总大小超过 " + (maxBytes / 1024) + " KiB");
        if (!entryFound) issues.add("缺少 index.html");
        validateManifest(manifest, issues);
        return List.copyOf(issues);
    }

    static List<String> validateInline(JsonNode inline) {
        if (!inline.isObject()) return List.of("内联代码必须为对象");
        JsonNode filesNode = inline.path("files");
        if (!filesNode.isObject()) return List.of("内联代码 files 必须为对象");
        Map<String, String> files = new java.util.LinkedHashMap<>();
        filesNode.fields().forEachRemaining(item -> {
            if (!item.getValue().isTextual()) files.put(item.getKey(), "\u0000");
            else files.put(item.getKey(), item.getValue().asText());
        });
        List<String> issues = new ArrayList<>(validate(files, inline.path("manifest"), MAX_INLINE_FILES, MAX_INLINE_BYTES));
        filesNode.fields().forEachRemaining(item -> {
            if (!item.getValue().isTextual()) issues.add("内联代码文件必须为文本: " + item.getKey());
        });
        return List.copyOf(issues);
    }

    private static void validateManifest(JsonNode manifest, List<String> issues) {
        if (manifest.isMissingNode() || manifest.isNull()) return;
        if (!manifest.isObject()) {
            issues.add("代码 manifest 必须为对象");
            return;
        }
        JsonNode capabilities = manifest.path("capabilities");
        if (!capabilities.isMissingNode()) {
            if (!capabilities.isArray() || capabilities.size() > CAPABILITIES.size()) issues.add("代码 capabilities 不合法");
            else for (JsonNode capability : capabilities)
                if (!capability.isTextual() || !CAPABILITIES.contains(capability.asText()))
                    issues.add("代码请求了未授权能力");
        }
    }

    private static void scanText(String path, String extension, String content, List<String> issues) {
        String text = content.toLowerCase(Locale.ROOT);
        if ("js".equals(extension)) {
            SCRIPT_DENYLIST.stream().filter(text::contains)
                .forEach(token -> issues.add(path + " 包含禁止能力: " + token));
            if (text.matches("(?s).*\\b(import|export)\\s+.*(?:https?:|//).*"))
                issues.add(path + " 只允许相对 ES 模块导入");
        }
        if ("html".equals(extension) && (text.contains("<form") || text.contains("<iframe")
            || text.matches("(?s).*<meta[^>]+http-equiv\\s*=\\s*['\"]?refresh.*")
            || text.matches("(?s).*<script[^>]+src\\s*=\\s*['\"]?(?:https?:|//).*")))
            issues.add(path + " 包含禁止的表单、iframe、刷新跳转或远程脚本");
        if ("css".equals(extension) && (text.contains("@import")
            || text.matches("(?s).*url\\s*\\(\\s*['\"]?(?:https?:|//).*")))
            issues.add(path + " 包含远程样式资源");
    }

    private static boolean safePath(String path) {
        return !path.isBlank() && !path.startsWith("/") && !path.contains("../")
            && !path.contains("/..") && !path.contains("\0") && path.length() <= 255;
    }

    private static String extension(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? "" : path.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
