package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict CMS V2 contract validation shared by draft validation and publication. */
@Component
public class PortalSiteConfigValidator {
    private static final Set<String> SCENARIOS = Set.of("party", "internal-policy", "product-help");
    private static final Set<String> LAYOUTS = Set.of("single", "sidebar-left", "sidebar-right", "twelve-grid");
    private static final Set<String> SHELL_LAYOUTS = Set.of("editorial-authority", "sidebar-workbench", "search-center");
    private static final Set<String> VISUAL_PACKS = Set.of("party-authority", "policy-workbench", "help-product");
    private static final Set<String> REGIONS = Set.of("header", "main", "sidebar", "footer");
    private static final Set<String> BLOCKS = Set.of(
        "hero-search", "category-grid", "recent-documents", "current-topic", "reading-history",
        "favorites", "announcement", "quick-ask", "category-tree", "category-cards",
        "hot-searches", "recommended-articles", "pinned-content", "faq-list", "release-notes",
        "validity-dashboard", "document-timeline", "related-documents", "download-area",
        "sop-steps", "process-navigation", "role-entry", "learning-path", "ai-assistant",
        "suggested-questions", "no-answer-help", "human-help", "rich-text", "image-banner",
        "metric-cards", "feedback"
    );
    private static final Set<String> DATA_SOURCES = Set.of(
        "documents", "categories", "topics", "favorites", "history", "announcements",
        "static", "search", "assistant"
    );
    private static final Set<String> MODULES = Set.of(
        "portal.home", "portal.library", "portal.qa", "portal.topics",
        "portal.custom-pages", "portal.favorites", "portal.profile"
    );
    private static final Set<String> NODE_TYPES = Set.of(
        "section", "container", "grid", "stack", "component", "sandbox", "symbol-ref");
    private static final Set<String> CONTAINER_NODE_TYPES = Set.of("section", "container", "grid", "stack");
    private static final Set<String> CORE_COMPONENTS = Set.of(
        "content-results", "document-reader", "ai-conversation", "topic-directory",
        "favorite-list", "profile-card", "portal-navigation", "account-entry");
    private static final Map<String, String> REQUIRED_PAGE_COMPONENTS = Map.of(
        "library", "content-results",
        "search", "content-results",
        "content", "document-reader",
        "ask", "ai-conversation",
        "topics", "topic-directory",
        "favorites", "favorite-list",
        "profile", "profile-card");
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_-]{1,63}$");
    private static final Pattern NAVIGATION_TARGET = Pattern.compile(
        "^(home|library|ask|topics|favorites|profile|page/[a-z][a-z0-9-]{1,63})$");
    private static final Pattern DANGEROUS_KEY = Pattern.compile(
        "(?i)^(api|apiUrl|endpoint|sql|script|javascript|eventHandler|componentUrl)$");

    public List<String> validate(JsonNode config, String expectedSiteKey) {
        List<String> issues = new ArrayList<>();
        if (config == null || !config.isObject()) return List.of("配置必须是 JSON 对象");
        int schemaVersion = config.path("schemaVersion").asInt(-1);
        if (schemaVersion == 3) return validateV3(config, expectedSiteKey);
        if (schemaVersion != 2) issues.add("schemaVersion 必须为 2 或 3");
        requireObject(config, "site", issues);
        requireObject(config, "shell", issues);
        requireObject(config, "theme", issues);
        requireObject(config, "contentScope", issues);
        requireObject(config, "search", issues);
        requireObject(config, "assistant", issues);
        requireObject(config, "pages", issues);

        JsonNode site = config.path("site");
        String siteKey = site.path("siteKey").asText("");
        if (!IDENTIFIER.matcher(siteKey).matches()) issues.add("site.siteKey 不合法");
        if (StringUtils.hasText(expectedSiteKey) && !expectedSiteKey.equals(siteKey))
            issues.add("site.siteKey 与目标站点不一致");
        if (!SCENARIOS.contains(site.path("scenario").asText())) issues.add("site.scenario 不受支持");
        if (!StringUtils.hasText(site.path("name").asText()) || site.path("name").asText().length() > 255)
            issues.add("site.name 必须为 1–255 个字符");

        validateNavigation(config.path("shell").path("navigation"), config.path("pages"), issues);
        String shellLayout = config.path("shell").path("layout").asText("");
        if (!shellLayout.isBlank() && !SHELL_LAYOUTS.contains(shellLayout)) issues.add("shell.layout 不受支持");
        validateModules(config.path("modules"), issues);
        validatePages(config.path("pages"), issues);
        validateScope(config.path("contentScope"), issues);
        validateTheme(config.path("theme"), issues);
        scanDangerousFields(config, "", issues);
        return List.copyOf(issues);
    }

    private List<String> validateV3(JsonNode config, String expectedSiteKey) {
        List<String> issues = new ArrayList<>();
        requireObject(config, "site", issues);
        requireObject(config, "shell", issues);
        requireObject(config, "theme", issues);
        requireObject(config, "contentScope", issues);
        requireObject(config, "search", issues);
        requireObject(config, "assistant", issues);
        requireObject(config, "pages", issues);
        requireObject(config, "symbols", issues);
        if (!config.path("packages").isArray()) issues.add("packages 必须为数组");

        JsonNode site = config.path("site");
        String siteKey = site.path("siteKey").asText("");
        if (!IDENTIFIER.matcher(siteKey).matches()) issues.add("site.siteKey 不合法");
        if (StringUtils.hasText(expectedSiteKey) && !expectedSiteKey.equals(siteKey))
            issues.add("site.siteKey 与目标站点不一致");
        if (!SCENARIOS.contains(site.path("scenario").asText())) issues.add("site.scenario 不受支持");
        if (!StringUtils.hasText(site.path("name").asText()) || site.path("name").asText().length() > 255)
            issues.add("site.name 必须为 1–255 个字符");

        validateNavigation(config.path("shell").path("navigation"), config.path("pages"), issues);
        validateModules(config.path("modules"), issues);
        validateScope(config.path("contentScope"), issues);
        validateTheme(config.path("theme"), issues);
        validateV3Shell(config.path("shell"), config.path("symbols"), issues);
        validateV3Pages(config.path("pages"), config.path("symbols"), issues);
        validateV3Symbols(config.path("symbols"), issues);
        validatePackages(config.path("packages"), issues);
        scanDangerousFields(config, "", issues);
        return List.copyOf(issues);
    }

    private void validateV3Shell(JsonNode shell, JsonNode symbols, List<String> issues) {
        Set<String> ids = new LinkedHashSet<>();
        NodeStats header = validateNode(shell.path("header"), "shell.header", 1, ids, symbols, new LinkedHashSet<>(), issues);
        NodeStats footer = validateNode(shell.path("footer"), "shell.footer", 1, ids, symbols, new LinkedHashSet<>(), issues);
        if (header.componentCounts().getOrDefault("portal-navigation", 0) != 1)
            issues.add("全局页头必须包含且仅包含一个 portal-navigation");
        if (footer.componentCounts().getOrDefault("account-entry", 0) != 1)
            issues.add("全局页脚必须包含且仅包含一个 account-entry");
    }

    private void validateV3Pages(JsonNode pages, JsonNode symbols, List<String> issues) {
        if (!pages.isObject() || pages.isEmpty()) {
            issues.add("pages 至少包含一个页面");
            return;
        }
        pages.fields().forEachRemaining(entry -> {
            JsonNode page = entry.getValue();
            String slug = page.path("slug").asText(entry.getKey());
            String kind = page.path("kind").asText("custom");
            if (!IDENTIFIER.matcher(slug).matches()) issues.add("页面 slug 不合法: " + slug);
            Set<String> ids = new LinkedHashSet<>();
            NodeStats stats = validateNode(page.path("root"), "pages." + slug + ".root", 1,
                ids, symbols, new LinkedHashSet<>(), issues);
            if (stats.nodes() > 300) issues.add("页面 " + slug + " 节点数超过 300");
            if (stats.sandboxes() > 5) issues.add("页面 " + slug + " 沙箱组件超过 5 个");
            String required = REQUIRED_PAGE_COMPONENTS.get(kind);
            if (required != null && stats.componentCounts().getOrDefault(required, 0) != 1)
                issues.add("页面 " + slug + " 必须包含且仅包含一个 " + required);
        });
    }

    private void validateV3Symbols(JsonNode symbols, List<String> issues) {
        if (!symbols.isObject()) return;
        symbols.fields().forEachRemaining(entry -> {
            if (!IDENTIFIER.matcher(entry.getKey()).matches()) {
                issues.add("可复用区块 ID 不合法: " + entry.getKey());
                return;
            }
            Set<String> ids = new LinkedHashSet<>();
            validateNode(entry.getValue().path("root"), "symbols." + entry.getKey(), 1, ids,
                symbols, new LinkedHashSet<>(Set.of(entry.getKey())), issues);
        });
    }

    private NodeStats validateNode(JsonNode node, String path, int depth, Set<String> ids, JsonNode symbols,
                                   Set<String> symbolTrail, List<String> issues) {
        if (!node.isObject()) {
            issues.add(path + " 必须是布局节点");
            return NodeStats.empty();
        }
        if (depth > 8) issues.add(path + " 嵌套深度超过 8");
        String id = node.path("id").asText("");
        String type = node.path("type").asText("");
        if (!IDENTIFIER.matcher(id).matches()) issues.add(path + " 节点 ID 不合法");
        if (!ids.add(id)) issues.add(path + " 节点 ID 重复: " + id);
        if (!NODE_TYPES.contains(type)) {
            issues.add(path + " 节点类型不支持: " + type);
            return new NodeStats(1, 0, Map.of());
        }
        validateResponsive(node.path("layout"), path, issues);
        int sandboxes = "sandbox".equals(type) ? 1 : 0;
        Map<String, Integer> components = new java.util.LinkedHashMap<>();
        if ("component".equals(type)) {
            String component = node.path("component").asText("");
            if (!BLOCKS.contains(component) && !CORE_COMPONENTS.contains(component))
                issues.add(path + " 包含未知组件: " + component);
            components.put(component, 1);
            validateDataSource(node.path("dataSource"), path, issues);
            validateActions(node.path("actions"), path, issues);
        }
        if ("sandbox".equals(type)) {
            if (!IDENTIFIER.matcher(node.path("packageId").asText("")).matches()
                || !StringUtils.hasText(node.path("version").asText()))
                issues.add(path + " 沙箱包标识或版本不合法");
            int height = node.path("height").asInt(360);
            if (height < 120 || height > 1200) issues.add(path + " 沙箱高度必须为 120–1200");
        }
        if ("symbol-ref".equals(type)) {
            String symbolId = node.path("symbolId").asText("");
            if (!IDENTIFIER.matcher(symbolId).matches() || !symbols.has(symbolId)) {
                issues.add(path + " 引用了未知可复用区块: " + symbolId);
            } else if (!symbolTrail.add(symbolId)) {
                issues.add(path + " 存在可复用区块循环引用: " + symbolId);
            } else {
                NodeStats nested = validateNode(symbols.path(symbolId).path("root"), path + "->" + symbolId,
                    depth + 1, ids, symbols, new LinkedHashSet<>(symbolTrail), issues);
                return new NodeStats(1 + nested.nodes(), sandboxes + nested.sandboxes(),
                    mergeCounts(components, nested.componentCounts()));
            }
        }
        JsonNode children = node.path("children");
        if (CONTAINER_NODE_TYPES.contains(type)) {
            if (!children.isArray()) issues.add(path + " 容器节点必须包含 children");
            else if (children.size() > 50) issues.add(path + " 直接子节点超过 50");
        } else if (!children.isMissingNode()) {
            issues.add(path + " 非容器节点不能包含 children");
        }
        int nodes = 1;
        if (children.isArray()) {
            int index = 0;
            for (JsonNode child : children) {
                NodeStats nested = validateNode(child, path + ".children[" + index++ + "]", depth + 1,
                    ids, symbols, symbolTrail, issues);
                nodes += nested.nodes();
                sandboxes += nested.sandboxes();
                mergeCounts(components, nested.componentCounts());
            }
        }
        return new NodeStats(nodes, sandboxes, components);
    }

    private void validateResponsive(JsonNode layout, String path, List<String> issues) {
        if (layout.isMissingNode()) return;
        if (!layout.isObject()) {
            issues.add(path + ".layout 必须为对象");
            return;
        }
        for (String key : List.of("span", "order", "gap", "hidden", "align", "direction")) {
            JsonNode value = layout.path(key);
            if (!value.isMissingNode() && !value.isObject()) issues.add(path + ".layout." + key + " 必须为响应式对象");
        }
        validateSpan(layout.path("span").path("desktop"), 12, path + ".layout.span.desktop", issues);
        validateSpan(layout.path("span").path("tablet"), 8, path + ".layout.span.tablet", issues);
        validateSpan(layout.path("span").path("mobile"), 4, path + ".layout.span.mobile", issues);
    }

    private void validateSpan(JsonNode value, int max, String path, List<String> issues) {
        if (!value.isMissingNode() && (!value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > max))
            issues.add(path + " 必须为 1–" + max);
    }

    private void validateDataSource(JsonNode source, String path, List<String> issues) {
        if (source.isMissingNode()) return;
        if (!source.isObject() || !DATA_SOURCES.contains(source.path("source").asText()))
            issues.add(path + " 数据源不在白名单");
    }

    private void validateActions(JsonNode actions, String path, List<String> issues) {
        if (actions.isMissingNode()) return;
        Set<String> allowed = Set.of("navigate", "set-filter", "search", "ask", "open-content",
            "dialog", "feedback", "analytics");
        if (!actions.isArray() || actions.size() > 20) {
            issues.add(path + " actions 必须为不超过 20 项的数组");
            return;
        }
        for (JsonNode action : actions)
            if (!allowed.contains(action.path("type").asText())) issues.add(path + " 包含非法动作");
    }

    private void validatePackages(JsonNode packages, List<String> issues) {
        if (!packages.isArray() || packages.size() > 100) {
            issues.add("packages 必须为不超过 100 项的数组");
            return;
        }
        for (JsonNode item : packages) {
            if (!IDENTIFIER.matcher(item.path("packageId").asText("")).matches()
                || !StringUtils.hasText(item.path("version").asText())
                || !Set.of("platform", "site").contains(item.path("source").asText()))
                issues.add("packages 包含非法引用");
        }
    }

    private Map<String, Integer> mergeCounts(Map<String, Integer> target, Map<String, Integer> source) {
        source.forEach((key, value) -> target.merge(key, value, Integer::sum));
        return target;
    }

    private record NodeStats(int nodes, int sandboxes, Map<String, Integer> componentCounts) {
        static NodeStats empty() {
            return new NodeStats(0, 0, Map.of());
        }
    }

    private void validateNavigation(JsonNode navigation, JsonNode pages, List<String> issues) {
        if (!navigation.isArray() || navigation.size() > 20) {
            issues.add("shell.navigation 必须为不超过 20 项的数组");
            return;
        }
        for (JsonNode item : navigation) {
            String target = item.path("target").asText("");
            if (!NAVIGATION_TARGET.matcher(target).matches()) {
                issues.add("导航目标不受支持: " + target);
            } else if (target.startsWith("page/") && !pages.has(target.substring(5))) {
                issues.add("导航目标页面不存在: " + target);
            }
        }
    }

    private void validateModules(JsonNode modules, List<String> issues) {
        if (!modules.isObject()) {
            issues.add("modules 必须为对象");
            return;
        }
        modules.fields().forEachRemaining(entry -> {
            if (!MODULES.contains(entry.getKey())) issues.add("未知模块: " + entry.getKey());
            if (!entry.getValue().isBoolean()) issues.add("模块开关必须为布尔值: " + entry.getKey());
        });
        if (modules.path("portal.home").isBoolean() && !modules.path("portal.home").asBoolean())
            issues.add("核心模块 portal.home 不能关闭");
        if (modules.path("portal.profile").isBoolean() && !modules.path("portal.profile").asBoolean())
            issues.add("核心模块 portal.profile 不能关闭");
    }

    private void validatePages(JsonNode pages, List<String> issues) {
        if (!pages.isObject() || pages.isEmpty()) {
            issues.add("pages 至少包含一个页面");
            return;
        }
        pages.fields().forEachRemaining(entry -> {
            JsonNode page = entry.getValue();
            if (!page.isObject()) {
                issues.add("页面 " + entry.getKey() + " 必须为对象");
                return;
            }
            String slug = page.path("slug").asText(entry.getKey());
            if (!IDENTIFIER.matcher(slug).matches()) issues.add("页面 slug 不合法: " + slug);
            if (!LAYOUTS.contains(page.path("layout").asText())) issues.add("页面布局不受支持: " + slug);
            JsonNode regions = page.path("regions");
            if (!regions.isObject() || !regions.path("main").isArray()) {
                issues.add("页面 " + slug + " 必须包含 regions.main");
                return;
            }
            regions.fields().forEachRemaining(region -> {
                if (!REGIONS.contains(region.getKey()) || !region.getValue().isArray()) {
                    issues.add("页面 " + slug + " 包含非法区域 " + region.getKey());
                    return;
                }
                for (JsonNode block : region.getValue()) validateBlock(slug, block, issues);
            });
            validateExtensions(slug, page.path("extensions"), issues);
        });
    }

    private void validateBlock(String page, JsonNode block, List<String> issues) {
        if (!block.isObject()) {
            issues.add("页面 " + page + " 包含非对象区块");
            return;
        }
        String id = block.path("id").asText();
        String type = block.path("type").asText();
        if (!IDENTIFIER.matcher(id).matches()) issues.add("页面 " + page + " 的区块 ID 不合法");
        if (!BLOCKS.contains(type)) issues.add("未知区块类型: " + type);
        int span = block.path("span").asInt(12);
        if (span < 1 || span > 12) issues.add("区块 " + id + " 的 span 必须为 1–12");
        JsonNode dataSource = block.path("dataSource");
        if (!dataSource.isMissingNode()) {
            if (!dataSource.isObject() || !DATA_SOURCES.contains(dataSource.path("source").asText()))
                issues.add("区块 " + id + " 的数据源不在白名单");
        }
    }

    private void validateScope(JsonNode scope, List<String> issues) {
        for (String key : List.of("spaceCodes", "topicCodes", "contentTypes", "validityStatuses")) {
            JsonNode value = scope.path(key);
            if (!value.isMissingNode() && (!value.isArray() || value.size() > 100))
                issues.add("contentScope." + key + " 必须为不超过 100 项的数组");
        }
        if (!scope.path("allSpaces").asBoolean(false) && scope.path("spaceCodes").isEmpty())
            issues.add("未选择全空间时必须至少配置一个 spaceCode");
    }

    private void validateTheme(JsonNode theme, List<String> issues) {
        String pack = theme.path("pack").asText("");
        if (!pack.isBlank() && !VISUAL_PACKS.contains(pack)) issues.add("theme.pack 不受支持");
        String css = theme.path("customCss").asText("");
        if (css.getBytes(StandardCharsets.UTF_8).length > 30 * 1024) issues.add("theme.customCss 超过 30 KiB");
        String normalized = css.toLowerCase();
        if (normalized.contains("@import") || normalized.contains("javascript:")
            || normalized.contains("expression(") || normalized.matches("(?s).*url\\s*\\(\\s*['\"]?https?://.*"))
            issues.add("theme.customCss 包含禁止的导入、脚本或远程资源");
        if (Pattern.compile("(?i)(^|[},])\\s*(html|body|:root|\\.login|\\.admin|\\.console)(\\W|$)")
            .matcher(css).find()) issues.add("theme.customCss 不允许使用全局或后台选择器");
    }

    private void validateExtensions(String page, JsonNode extensions, List<String> issues) {
        if (extensions.isMissingNode()) return;
        if (!extensions.isArray() || extensions.size() > 20) {
            issues.add("页面 " + page + " 的 extensions 必须为不超过 20 项的数组");
            return;
        }
        for (JsonNode extension : extensions) {
            if (!extension.isObject()) {
                issues.add("页面 " + page + " 包含非对象扩展绑定");
                continue;
            }
            if (!IDENTIFIER.matcher(extension.path("extensionId").asText()).matches()
                || !StringUtils.hasText(extension.path("version").asText())
                || !IDENTIFIER.matcher(extension.path("slotKey").asText()).matches())
                issues.add("页面 " + page + " 的扩展标识不合法");
            String region = extension.path("region").asText("main");
            if (!REGIONS.contains(region)) issues.add("页面 " + page + " 的扩展区域不受支持");
            if (!extension.path("config").isMissingNode() && !extension.path("config").isObject())
                issues.add("页面 " + page + " 的扩展配置必须为对象");
        }
    }

    private void scanDangerousFields(JsonNode node, String path, List<String> issues) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (DANGEROUS_KEY.matcher(name).matches()) issues.add("禁止的配置字段: " + path + name);
                else scanDangerousFields(node.get(name), path + name + ".", issues);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) scanDangerousFields(child, path, issues);
        }
    }

    private void requireObject(JsonNode config, String name, List<String> issues) {
        if (!config.path(name).isObject()) issues.add(name + " 必须为对象");
    }
}
