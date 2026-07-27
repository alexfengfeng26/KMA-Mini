package com.kma.common.security;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Canonical permission implications and compatibility aliases shared by JWT and method security. */
public final class KmaPermissionCatalog {
    private static final Map<String, Set<String>> LEGACY_BUNDLES = legacyBundles();
    private static final Map<String, String> PARENTS = parents();

    private KmaPermissionCatalog() {}

    public static Set<String> expand(Set<String> permissions) {
        LinkedHashSet<String> effective = new LinkedHashSet<>();
        if (permissions != null) effective.addAll(permissions);
        boolean changed;
        do {
            changed = false;
            for (String permission : Set.copyOf(effective)) {
                String canonical = canonicalRequirement(permission);
                if (!canonical.equals(permission)) changed |= effective.add(canonical);
                Set<String> bundle = LEGACY_BUNDLES.get(permission);
                if (bundle != null) changed |= effective.addAll(bundle);
                String parent = PARENTS.get(canonical);
                if (parent != null) changed |= effective.add(parent);
            }
        } while (changed);
        return effective;
    }

    public static boolean has(Set<String> permissions, String required) {
        if (permissions == null || required == null || required.isBlank()) return false;
        Set<String> effective = expand(permissions);
        if (effective.contains("kma:admin") || effective.contains(required)) return true;
        String canonical = canonicalRequirement(required);
        return effective.contains(canonical);
    }

    static String canonicalRequirement(String permission) {
        if (permission == null) return "";
        return switch (permission) {
            case "knowledge:space:add" -> "space:create";
            case "knowledge:space:edit" -> "space:update";
            case "knowledge:space:remove" -> "space:delete";
            case "knowledge:space:auth" -> "space:acl:manage";
            case "knowledge:space:query", "knowledge:space:list" -> "space:read";
            case "knowledge:dataset:add" -> "dataset:create";
            case "knowledge:dataset:edit" -> "dataset:update";
            case "knowledge:dataset:remove" -> "dataset:delete";
            case "knowledge:dataset:query", "knowledge:dataset:list" -> "dataset:read";
            case "knowledge:ingest:add" -> "document:ingest";
            case "knowledge:ingest:edit" -> "document:reindex";
            case "knowledge:ingest:remove" -> "document:delete";
            case "knowledge:ingest:query" -> "document:read";
            case "knowledge:feed:list" -> "task:read";
            case "knowledge:feed:retry" -> "task:retry";
            case "knowledge:retrieve:query" -> "retrieval:use";
            case "knowledge:qa:query" -> "qa:use";
            case "knowledge:chat:query" -> "chat:read";
            case "knowledge:call-log:list", "knowledge:call-log:query" -> "audit:call:read";
            case "knowledge:metrics:query" -> "dashboard:read";
            default -> permission;
        };
    }

    private static Map<String, Set<String>> legacyBundles() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put("user:manage", Set.of("user:read", "user:create", "user:status:update",
            "user:role:assign", "user:password:reset", "user:token:revoke", "role:read",
            "role:create", "role:update", "role:delete", "permission:read", "org:read",
            "org:create", "org:update", "org:move", "org:delete", "org:member:manage"));
        result.put("space:manage", Set.of("space:read", "space:create", "space:update", "space:delete",
            "space:acl:manage", "space:reindex", "dataset:read", "dataset:create", "dataset:update",
            "dataset:delete", "dataset:status:update", "embedding:rebuild", "embedding:activate"));
        result.put("audit:read", Set.of("dashboard:read", "audit:call:read", "audit:security:read"));
        result.put("security:audit", Set.of("audit:security:read"));
        result.put("model:manage", Set.of("model:read", "model:create", "model:update"));
        result.put("evaluation:manage", Set.of("evaluation:read", "evaluation:create",
            "evaluation:case:create", "evaluation:run", "evaluation:gate:update", "evaluation:release:assert"));
        result.put("storage:manage", Set.of("storage:read", "storage:reconcile", "storage:cleanup"));
        result.put("portal:configure", Set.of("portal-site:read", "portal-site:create",
            "portal-site:update", "portal-site:delete", "portal-page:edit", "portal-theme:manage",
            "portal-asset:manage", "portal-site:review", "portal-site:publish",
            "portal-analytics:read", "portal-extension:read", "portal-code:read",
            "portal-code:edit", "portal-code:publish", "portal-code:revoke"));
        return Map.copyOf(result);
    }

    private static Map<String, String> parents() {
        Map<String, String> result = new LinkedHashMap<>();
        addChildren(result, "space:read", "space:create", "space:update", "space:delete", "space:acl:manage", "space:reindex");
        addChildren(result, "dataset:read", "dataset:create", "dataset:update", "dataset:delete",
            "dataset:status:update", "embedding:rebuild", "embedding:activate");
        addChildren(result, "document:read", "document:ingest", "document:reindex", "document:delete");
        addChildren(result, "task:read", "task:retry");
        addChildren(result, "storage:read", "storage:reconcile", "storage:cleanup");
        addChildren(result, "qa:use", "chat:read");
        addChildren(result, "model:read", "model:create", "model:update");
        addChildren(result, "evaluation:read", "evaluation:create", "evaluation:case:create",
            "evaluation:run", "evaluation:gate:update", "evaluation:release:assert");
        addChildren(result, "user:read", "user:create", "user:status:update", "user:role:assign",
            "user:password:reset", "user:token:revoke");
        addChildren(result, "role:read", "role:create", "role:update", "role:delete", "permission:read");
        addChildren(result, "org:read", "org:create", "org:update", "org:move", "org:delete", "org:member:manage");
        addChildren(result, "content:read", "content:create", "content:update", "content:submit",
            "content:review", "content:publish", "topic:manage", "portal:configure");
        addChildren(result, "portal-site:read", "portal-site:create", "portal-site:update",
            "portal-site:delete", "portal-page:edit", "portal-theme:manage", "portal-asset:manage",
            "portal-site:review", "portal-site:publish", "portal-analytics:read", "portal-extension:read");
        addChildren(result, "portal-code:read", "portal-code:edit", "portal-code:publish", "portal-code:revoke");
        return Map.copyOf(result);
    }

    private static void addChildren(Map<String, String> target, String parent, String... children) {
        for (String child : children) target.put(child, parent);
    }
}
