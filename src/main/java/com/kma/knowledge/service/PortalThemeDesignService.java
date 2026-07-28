package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.ContentSecurityService;
import com.kma.common.security.KmaIdentityContext;
import com.kma.knowledge.client.llm.LlmChatResponse;
import com.kma.knowledge.client.llm.PortalDesignLlmClient;
import com.kma.knowledge.dto.PortalThemeDesignProposalRequest;
import com.kma.knowledge.dto.PortalThemeDesignProposalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalThemeDesignService {
    private static final int MAX_AI_CONTEXT_BYTES = 524_288;

    private final ObjectMapper objectMapper;
    private final PortalDesignLlmClient client;
    private final PortalThemeService themeService;
    private final ContentSecurityService contentSecurityService;

    public PortalThemeDesignProposalResponse propose(
        String siteKey, Long themeVersionId, PortalThemeDesignProposalRequest request) {
        themeService.assertEditable(siteKey, themeVersionId, request.getExpectedLockVersion());
        int contextBytes = request.getFiles().entrySet().stream()
            .mapToInt(entry -> entry.getKey().getBytes(StandardCharsets.UTF_8).length
                + value(entry.getValue()).getBytes(StandardCharsets.UTF_8).length)
            .sum();
        if (contextBytes > MAX_AI_CONTEXT_BYTES)
            throw new KmaException(413, "PORTAL_THEME_AI_CONTEXT_TOO_LARGE");

        ContentSecurityService.Inspection inspected = contentSecurityService.inspectUserInput(
            request.getInstruction(), "portal-theme-design:" + siteKey);
        List<Map<String, String>> messages = messages(inspected.sanitized(), request.getFiles());
        long startedAt = System.nanoTime();
        LlmChatResponse llm = client.generate(messages, "kma-theme-" + KmaIdentityContext.getUserId());
        String safeOutput = contentSecurityService.processModelOutput(
            llm.getContent(), "portal-theme-design:" + siteKey).sanitized();
        JsonNode proposal = parse(safeOutput);
        JsonNode proposedFiles = proposal.path("files");
        if (!proposedFiles.isObject()) throw new KmaException(502, "AI_THEME_FILES_MISSING");

        Map<String, String> merged = new LinkedHashMap<>(request.getFiles());
        List<String> changed = new ArrayList<>();
        proposedFiles.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) throw new KmaException(502, "AI_THEME_FILE_INVALID");
            String path = PortalThemeSecurity.normalizePath(entry.getKey());
            String content = entry.getValue().asText();
            if (!content.equals(merged.get(path))) changed.add(path);
            merged.put(path, content);
        });
        List<String> issues = PortalThemeSecurity.validate(merged, objectMapper.createObjectNode()
            .put("entry", "layout.html")
            .set("capabilities", objectMapper.valueToTree(List.of(
                "page-context", "contents", "search", "ask", "analytics", "navigation"))));
        if (!issues.isEmpty()) throw new KmaException(422, "AI_THEME_PROPOSAL_UNSAFE: " + issues.getFirst());
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Portal theme AI completed, site={}, model={}, changedFiles={}, elapsedMs={}, promptTokens={}, completionTokens={}",
            siteKey, llm.getModel(), changed.size(), elapsedMs, llm.getPromptTokens(), llm.getCompletionTokens());
        return new PortalThemeDesignProposalResponse(
            llm.getModel(), proposal.path("summary").asText("已生成整站主题提案"),
            textList(proposal.path("warnings")), Map.copyOf(merged), List.copyOf(changed),
            number(llm.getPromptTokens()), number(llm.getCompletionTokens()));
    }

    private List<Map<String, String>> messages(String instruction, Map<String, String> files) {
        String system = """
            你是 KMA Portal Theme V4 整站主题设计师。只输出 JSON 对象：
            {"summary":"中文摘要","warnings":[],"files":{"需要修改的相对路径":"完整文件内容"}}
            可以修改 layout.html、pages/*.html、partials/*.html、styles/*.css、scripts/*.js。
            模板只使用普通 HTML、{{ value }}、{% if %}、{% for %}、静态 {% include "partials/x.html" %}、
            {% slot content %} 和受控 kma-widget/kma-link 标签。禁止三大括号、外部资源、CDN、fetch、
            XHR、WebSocket、Cookie、Storage、父窗口、eval、动态 import、表单、iframe 和远程 URL。
            JavaScript 只能调用 portal.context/data/navigation/search/ask/content/analytics。
            不要删除没有修改的文件，不要输出 Markdown 代码围栏。
            """;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruction", instruction);
        payload.put("files", files);
        try {
            return List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", objectMapper.writeValueAsString(payload)));
        } catch (Exception ex) {
            throw new KmaException(500, "PORTAL_THEME_AI_CONTEXT_INVALID");
        }
    }

    private JsonNode parse(String raw) {
        try {
            String value = value(raw).trim();
            if (value.startsWith("```")) {
                int start = value.indexOf('\n');
                int end = value.lastIndexOf("```");
                value = start >= 0 && end > start ? value.substring(start + 1, end).trim() : value;
            }
            JsonNode parsed = objectMapper.readTree(value);
            if (!parsed.isObject()) throw new IllegalArgumentException("not object");
            return parsed;
        } catch (Exception ex) {
            throw new KmaException(502, "AI_THEME_INVALID_JSON");
        }
    }

    private List<String> textList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> {
            if (item.isTextual() && result.size() < 20) result.add(item.asText());
        });
        return List.copyOf(result);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }
}
