package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static policy for full-site themes. Theme source is data and is never executed by the server. */
final class PortalThemeSecurity {
    static final int MAX_FILES = 100;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final Set<String> CAPABILITIES = Set.of(
        "page-context", "contents", "search", "ask", "analytics", "navigation");
    private static final Set<String> WIDGETS = Set.of(
        "content-list", "topic-directory", "document-reader", "ai-chat", "favorite-list", "profile-card");
    private static final Set<String> EXTENSIONS = Set.of(
        "html", "css", "js", "json", "svg", "png", "jpg", "jpeg", "gif", "webp", "woff2");
    private static final List<String> SCRIPT_DENYLIST = List.of(
        "fetch(", "xmlhttprequest", "websocket", "eventsource", "sendbeacon",
        "document.cookie", "localstorage", "sessionstorage", "window.parent", "window.top",
        "window.opener", "window.open(", "eval(", "new function(", "import(");
    private static final Pattern INCLUDE = Pattern.compile(
        "\\{%\\s*include\\s+['\"]([A-Za-z0-9_./-]+)['\"]\\s*%}");
    private static final Pattern WIDGET = Pattern.compile(
        "<kma-widget\\s+[^>]*name\\s*=\\s*['\"]([a-z0-9-]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern KMA_TAG = Pattern.compile(
        "<\\/?kma-([a-z0-9-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIQUID_TAG = Pattern.compile(
        "\\{%\\s*([a-z]+)\\b[^%]*%}", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODULE_IMPORT = Pattern.compile(
        "\\b(?:import|export)\\s+(?:[^'\"]*?\\s+from\\s+)?['\"]([^'\"]+)['\"]");
    private static final Pattern SDK_CAPABILITY = Pattern.compile(
        "\\bportal\\.(context|contents|data|search|ask|analytics|navigation)\\s*\\.", Pattern.CASE_INSENSITIVE);

    private PortalThemeSecurity() {}

    static List<String> validate(Map<String, String> files, JsonNode manifest) {
        List<String> issues = new ArrayList<>();
        if (files == null || files.isEmpty()) return List.of("主题文件不能为空");
        if (files.size() > MAX_FILES) issues.add("主题文件数量超过 " + MAX_FILES);
        long bytes = 0;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = normalizePath(entry.getKey());
            String source = entry.getValue() == null ? "" : entry.getValue();
            bytes += source.getBytes(StandardCharsets.UTF_8).length;
            if (!safePath(path)) {
                issues.add("文件路径不合法: " + path);
                continue;
            }
            String extension = extension(path);
            if (!EXTENSIONS.contains(extension)) issues.add(path + " 文件类型不受支持");
            if ("js".equals(extension)) scanScript(path, source, files, issues);
            if ("css".equals(extension)) scanCss(path, source, issues);
            if ("html".equals(extension)) scanTemplate(path, source, files, issues);
        }
        if (bytes > MAX_BYTES) issues.add("主题总大小超过 5 MiB");
        for (String required : List.of("layout.html", "pages/home.html", "styles/theme.css"))
            if (!files.containsKey(required)) issues.add("缺少必需文件: " + required);
        validateManifest(manifest, issues);
        validateCapabilityContract(files, manifest, issues);
        detectIncludeCycles(files, issues);
        return List.copyOf(new java.util.LinkedHashSet<>(issues));
    }

    private static void scanScript(
        String path, String source, Map<String, String> files, List<String> issues) {
        String normalized = source.toLowerCase(Locale.ROOT);
        SCRIPT_DENYLIST.stream().filter(normalized::contains)
            .forEach(token -> issues.add(path + " 包含禁止能力: " + token));
        if (normalized.matches("(?s).*\\b(import|export)\\s+.*(?:https?:|//).*"))
            issues.add(path + " 只允许相对 ES 模块导入");
        Matcher dependency = MODULE_IMPORT.matcher(source);
        while (dependency.find()) {
            String specifier = dependency.group(1);
            String target = resolveModule(path, specifier);
            if (!specifier.startsWith(".") || target == null || !files.containsKey(target))
                issues.add(path + " 只允许主题包内相对 ES 模块: " + specifier);
        }
    }

    private static void scanCss(String path, String source, List<String> issues) {
        String normalized = source.toLowerCase(Locale.ROOT);
        if (normalized.contains("@import")
            || normalized.matches("(?s).*url\\s*\\(\\s*['\"]?(?:https?:|//).*"))
            issues.add(path + " 包含远程样式资源");
    }

    private static void scanTemplate(String path, String source, Map<String, String> files, List<String> issues) {
        String normalized = source.toLowerCase(Locale.ROOT);
        if (normalized.contains("{{{") || normalized.contains("__proto__")
            || normalized.contains("constructor.") || normalized.contains("prototype."))
            issues.add(path + " 包含禁止的原始输出或原型访问");
        if (normalized.contains("<iframe") || normalized.contains("<form")
            || normalized.contains("<script")
            || normalized.matches("(?s).*\\son[a-z]+\\s*=.*")
            || normalized.matches("(?s).*<meta[^>]+http-equiv\\s*=\\s*['\"]?refresh.*")
            || normalized.matches("(?s).*<(script|link)[^>]+(?:src|href)\\s*=\\s*['\"]?(?:https?:|//).*"))
            issues.add(path + " 包含 iframe、表单、刷新跳转或远程资源");
        Matcher widget = WIDGET.matcher(source);
        while (widget.find()) if (!WIDGETS.contains(widget.group(1)))
            issues.add(path + " 引用了未知 KMA 组件: " + widget.group(1));
        Matcher include = INCLUDE.matcher(source);
        while (include.find()) {
            String target = include.group(1);
            if (!target.startsWith("partials/") || !files.containsKey(target))
                issues.add(path + " include 不存在或不在 partials/: " + target);
        }
        Matcher kmaTag = KMA_TAG.matcher(source);
        while (kmaTag.find()) if (!Set.of("slot", "widget", "link", "action", "rich-text").contains(kmaTag.group(1)))
            issues.add(path + " 包含未知 KMA 标签: " + kmaTag.group(1));
        Matcher liquidTag = LIQUID_TAG.matcher(source);
        while (liquidTag.find()) if (!Set.of(
            "if", "else", "endif", "for", "endfor", "include", "slot").contains(liquidTag.group(1)))
            issues.add(path + " 包含不受支持的 Liquid 标签: " + liquidTag.group(1));
    }

    private static void validateManifest(JsonNode manifest, List<String> issues) {
        if (manifest == null || manifest.isMissingNode() || manifest.isNull()) return;
        if (!manifest.isObject()) {
            issues.add("theme.json manifest 必须为对象");
            return;
        }
        JsonNode capabilities = manifest.path("capabilities");
        if (!capabilities.isMissingNode()) {
            if (!capabilities.isArray() || capabilities.size() > CAPABILITIES.size())
                issues.add("主题 capabilities 不合法");
            else for (JsonNode capability : capabilities)
                if (!capability.isTextual() || !CAPABILITIES.contains(capability.asText()))
                    issues.add("主题请求了未授权能力");
        }
    }

    /** A theme must declare every Portal SDK capability it actually calls. */
    private static void validateCapabilityContract(Map<String, String> files, JsonNode manifest, List<String> issues) {
        Set<String> declared = new HashSet<>();
        if (manifest != null && manifest.path("capabilities").isArray())
            manifest.path("capabilities").forEach(node -> declared.add(node.asText()));
        Set<String> required = new HashSet<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            String source = file.getValue() == null ? "" : file.getValue();
            Matcher sdk = SDK_CAPABILITY.matcher(source);
            while (sdk.find()) {
                String group = sdk.group(1).toLowerCase(Locale.ROOT);
                required.add("context".equals(group) ? "page-context" : "data".equals(group) ? "contents" : group);
            }
            if (file.getKey().endsWith(".html")) {
                Matcher widget = WIDGET.matcher(source);
                while (widget.find()) {
                    String name = widget.group(1);
                    if (Set.of("content-list", "topic-directory", "document-reader", "favorite-list", "profile-card").contains(name))
                        required.add("contents");
                    if ("ai-chat".equals(name)) required.add("ask");
                }
            }
        }
        required.stream().filter(capability -> !declared.contains(capability)).sorted()
            .forEach(capability -> issues.add("主题调用了未声明的 SDK 能力: " + capability));
    }

    private static void detectIncludeCycles(Map<String, String> files, List<String> issues) {
        for (String path : files.keySet())
            if (path.endsWith(".html") && hasCycle(path, path, files, new HashSet<>()))
                issues.add("模板 include 存在循环: " + path);
    }

    private static boolean hasCycle(String origin, String current, Map<String, String> files, Set<String> trail) {
        if (!trail.add(current)) return origin.equals(current);
        Matcher matcher = INCLUDE.matcher(files.getOrDefault(current, ""));
        while (matcher.find()) {
            String target = matcher.group(1);
            if (origin.equals(target) || hasCycle(origin, target, files, new HashSet<>(trail))) return true;
        }
        return false;
    }

    static String normalizePath(String raw) {
        return raw == null ? "" : raw.replace('\\', '/');
    }

    private static boolean safePath(String path) {
        return !path.isBlank() && !path.startsWith("/") && !path.contains("../")
            && !path.contains("/..") && !path.contains("\0") && path.length() <= 255;
    }

    private static String extension(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? "" : path.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static String resolveModule(String owner, String dependency) {
        if (!dependency.startsWith("./") && !dependency.startsWith("../")) return null;
        try {
            java.nio.file.Path parent = java.nio.file.Path.of(owner).getParent();
            if (parent == null) return null;
            String target = parent.resolve(dependency).normalize().toString().replace('\\', '/');
            if (target.startsWith("../") || !target.startsWith("scripts/") || !target.endsWith(".js")) return null;
            return target;
        } catch (Exception ex) {
            return null;
        }
    }
}
