package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kma.common.exception.KmaException;
import com.kma.common.security.ContentSecurityService;
import com.kma.common.security.KmaIdentityContext;
import com.kma.knowledge.client.llm.LlmChatResponse;
import com.kma.knowledge.client.llm.PortalDesignLlmClient;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.PortalDesignCapabilityResponse;
import com.kma.knowledge.dto.PortalDesignProposalRequest;
import com.kma.knowledge.dto.PortalDesignProposalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalDesignService {
    private static final int MAX_TARGET_BYTES = 262_144;
    private static final String COMPONENT_CATALOG = """
        hero-search, category-grid, recent-documents, current-topic, reading-history, favorites,
        announcement, quick-ask, category-tree, category-cards, hot-searches, recommended-articles,
        pinned-content, faq-list, release-notes, validity-dashboard, document-timeline,
        related-documents, download-area, sop-steps, process-navigation, role-entry, learning-path,
        ai-assistant, suggested-questions, no-answer-help, human-help, rich-text, image-banner,
        metric-cards, feedback, content-results, document-reader, ai-conversation, topic-directory,
        favorite-list, profile-card
        """;

    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;
    private final PortalDesignLlmClient client;
    private final PortalSiteService portalSiteService;
    private final PortalSiteConfigValidator validator;
    private final ContentSecurityService contentSecurityService;

    public PortalDesignCapabilityResponse capability() {
        KnowledgeProperties.PortalDesignProperties config = properties.getPortalDesign();
        String reason = null;
        if (!config.isEnabled()) reason = "AI_DESIGN_DISABLED";
        else if (!StringUtils.hasText(config.getApiKey())) reason = "DEEPSEEK_API_KEY_MISSING";
        return new PortalDesignCapabilityResponse(
            reason == null, "deepseek", config.getModel(), reason);
    }

    public PortalDesignProposalResponse propose(String siteKey, PortalDesignProposalRequest request) {
        PortalDesignCapabilityResponse capability = capability();
        if (!capability.available()) throw new KmaException(503, capability.reason());
        verifyVersion(siteKey, request);
        JsonNode config = request.getConfig();
        List<String> baseIssues = validator.validate(config, siteKey);
        if (!baseIssues.isEmpty()) throw new KmaException(422, "AI_DESIGN_BASE_CONFIG_INVALID");

        JsonNode page = config.path("pages").path(request.getPageSlug());
        if (!page.isObject()) throw new KmaException(404, "PORTAL_PAGE_NOT_FOUND");
        JsonNode target = "page".equals(request.getScope())
            ? page
            : findNode(page.path("root"), request.getNodeId());
        if (target == null || !target.isObject()) throw new KmaException(404, "PORTAL_NODE_NOT_FOUND");
        if ("node".equals(request.getScope()) && target.path("locked").asBoolean(false))
            throw new KmaException(409, "LOCKED_PORTAL_NODE");
        if (target.toString().getBytes(StandardCharsets.UTF_8).length > MAX_TARGET_BYTES)
            throw new KmaException(413, "AI_DESIGN_TARGET_TOO_LARGE");

        ContentSecurityService.Inspection instruction = contentSecurityService.inspectUserInput(
            request.getInstruction(), "portal-design:" + siteKey);
        List<Map<String, String>> messages = buildMessages(
            request.getScope(), request.getPageSlug(), instruction.sanitized(), target);
        long startedAt = System.nanoTime();
        LlmChatResponse result = client.generate(messages, "kma-" + KmaIdentityContext.getUserId());
        JsonNode proposal = parseProposal(contentSecurityService.processModelOutput(
            result.getContent(), "portal-design:" + siteKey).sanitized());
        JsonNode proposedTarget = proposal.path("target");
        if (!proposedTarget.isObject()) throw new KmaException(502, "AI_DESIGN_TARGET_MISSING");

        ObjectNode merged = config.deepCopy();
        applyTarget(merged, request, proposedTarget);
        JsonNode normalizedTarget = "page".equals(request.getScope())
            ? merged.path("pages").path(request.getPageSlug())
            : findNode(merged.path("pages").path(request.getPageSlug()).path("root"), request.getNodeId());
        preserveLockedNodes(target, normalizedTarget);
        List<String> issues = validator.validate(merged, siteKey);
        if (!issues.isEmpty()) throw new KmaException(422, "AI_DESIGN_PROPOSAL_INVALID: " + issues.get(0));

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Portal AI design completed, site={}, scope={}, model={}, elapsedMs={}, promptTokens={}, completionTokens={}",
            siteKey, request.getScope(), result.getModel(), elapsedMs,
            result.getPromptTokens(), result.getCompletionTokens());
        return new PortalDesignProposalResponse(
            request.getScope(), request.getPageSlug(), request.getNodeId(), result.getModel(),
            proposal.path("summary").asText("已生成布局优化提案"),
            textList(proposal.path("warnings")), normalizedTarget,
            value(result.getPromptTokens()), value(result.getCompletionTokens()));
    }

    private void verifyVersion(String siteKey, PortalDesignProposalRequest request) {
        Map<String, Object> version = portalSiteService.version(siteKey, request.getVersionId());
        int lockVersion = ((Number) version.getOrDefault("lockVersion", -1)).intValue();
        if (lockVersion != request.getExpectedLockVersion())
            throw new KmaException(409, "PORTAL_VERSION_CONFLICT");
    }

    private List<Map<String, String>> buildMessages(
        String scope, String pageSlug, String instruction, JsonNode target) {
        String system = """
            你是 KMA 门户布局设计器。只输出一个 JSON 对象，格式为：
            {"summary":"简短中文摘要","warnings":["可选警告"],"target":{完整目标对象}}
            只能使用 section、container、grid、stack、component 节点，以及以下组件：
            %s
            保留所有已有 locked=true 节点的 id、type、component 和自身属性。
            节点 id 必须匹配 ^[a-z][a-z0-9_-]{1,63}$，最大深度 8，每个容器最多 50 个子节点。
            禁止输出 JavaScript、API 地址、SQL、脚本、事件处理器或解释性 Markdown。
            """.formatted(COMPONENT_CATALOG);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("scope", scope);
        payload.put("pageSlug", pageSlug);
        payload.put("instruction", instruction);
        payload.set("target", target);
        return List.of(
            Map.of("role", "system", "content", system),
            Map.of("role", "user", "content", payload.toString()));
    }

    private JsonNode parseProposal(String value) {
        try {
            String normalized = value == null ? "" : value.trim();
            if (normalized.startsWith("```")) {
                int firstLine = normalized.indexOf('\n');
                int closing = normalized.lastIndexOf("```");
                normalized = firstLine >= 0 && closing > firstLine
                    ? normalized.substring(firstLine + 1, closing).trim() : normalized;
            }
            JsonNode parsed = objectMapper.readTree(normalized);
            if (!parsed.isObject()) throw new IllegalArgumentException("not object");
            return parsed;
        } catch (Exception ex) {
            throw new KmaException(502, "AI_DESIGN_INVALID_JSON");
        }
    }

    private void applyTarget(ObjectNode config, PortalDesignProposalRequest request, JsonNode proposedTarget) {
        ObjectNode pages = (ObjectNode) config.path("pages");
        ObjectNode currentPage = (ObjectNode) pages.path(request.getPageSlug());
        if ("page".equals(request.getScope())) {
            ObjectNode nextPage = proposedTarget.deepCopy();
            nextPage.put("slug", currentPage.path("slug").asText(request.getPageSlug()));
            nextPage.put("kind", currentPage.path("kind").asText("custom"));
            JsonNode currentRoot = currentPage.path("root");
            if (!nextPage.path("root").isObject()) throw new KmaException(422, "AI_DESIGN_ROOT_MISSING");
            ((ObjectNode) nextPage.path("root")).put("id", currentRoot.path("id").asText());
            pages.set(request.getPageSlug(), nextPage);
            return;
        }
        ObjectNode nextNode = proposedTarget.deepCopy();
        nextNode.put("id", request.getNodeId());
        ObjectNode pageCopy = currentPage.deepCopy();
        JsonNode root = pageCopy.path("root");
        if (root.path("id").asText().equals(request.getNodeId())) pageCopy.set("root", nextNode);
        else if (!replaceNode(root, request.getNodeId(), nextNode))
            throw new KmaException(404, "PORTAL_NODE_NOT_FOUND");
        pages.set(request.getPageSlug(), pageCopy);
    }

    private void preserveLockedNodes(JsonNode before, JsonNode after) {
        Map<String, JsonNode> expected = new LinkedHashMap<>();
        collectLocked(before, expected);
        Map<String, JsonNode> actual = new LinkedHashMap<>();
        collectLocked(after, actual);
        expected.forEach((id, signature) -> {
            if (!signature.equals(actual.get(id))) throw new KmaException(422, "AI_DESIGN_LOCKED_NODE_CHANGED");
        });
    }

    private void collectLocked(JsonNode node, Map<String, JsonNode> result) {
        if (!node.isObject()) return;
        if (node.path("locked").asBoolean(false)) {
            ObjectNode signature = node.deepCopy();
            signature.remove("children");
            result.put(node.path("id").asText(), signature);
        }
        node.path("children").forEach(child -> collectLocked(child, result));
        if (node.has("root")) collectLocked(node.path("root"), result);
    }

    private JsonNode findNode(JsonNode root, String nodeId) {
        if (!StringUtils.hasText(nodeId) || !root.isObject()) return null;
        if (nodeId.equals(root.path("id").asText())) return root;
        for (JsonNode child : root.path("children")) {
            JsonNode found = findNode(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private boolean replaceNode(JsonNode root, String nodeId, ObjectNode replacement) {
        JsonNode children = root.path("children");
        if (!(children instanceof ArrayNode array)) return false;
        for (int index = 0; index < array.size(); index++) {
            JsonNode child = array.get(index);
            if (nodeId.equals(child.path("id").asText())) {
                array.set(index, replacement);
                return true;
            }
            if (replaceNode(child, nodeId, replacement)) return true;
        }
        return false;
    }

    private List<String> textList(JsonNode value) {
        List<String> result = new ArrayList<>();
        if (value.isArray()) value.forEach(item -> {
            if (item.isTextual() && result.size() < 20) result.add(item.asText());
        });
        return List.copyOf(result);
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }
}
