package com.kma.common.security;

import com.kma.common.exception.KmaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ContentSecurityService {
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");
    private static final List<Pattern> INJECTION = List.of(
        Pattern.compile("(?i)ignore\\s+(all\\s+)?(?:(previous|prior)\\s+)?(?:system\\s+)?(instructions?|prompts?)"),
        Pattern.compile("(?i)(reveal|show|print|leak).{0,20}(system\\s+prompt|hidden\\s+instructions?)"),
        Pattern.compile("(?i)developer\\s+mode|jailbreak|DAN\\s+mode"),
        Pattern.compile("忽略.{0,12}(之前|以上|系统).{0,12}(指令|提示)"),
        Pattern.compile("(泄露|显示|输出).{0,12}(系统提示词|隐藏指令)"),
        Pattern.compile("你现在是.{0,20}(不受限制|开发者模式|另一个助手)"));

    private final KmaSecurityProperties properties;
    private final SecurityAuditService auditService;

    public Inspection inspectUserInput(String value, String resource) {
        Inspection result = inspect(value, true);
        auditIfNeeded("user_input", result.blocked() ? "high" : "warning",
            result.blocked() ? "blocked" : "redacted", resource, value, result.flags());
        if (result.blocked() && properties.getContent().isBlockPromptInjection()) {
            throw new KmaException(400, "检测到疑似 Prompt 注入指令，请修改问题后重试");
        }
        return result;
    }

    public Inspection sanitizeReference(String value, String resource) {
        Inspection result = inspect(value, false);
        auditIfNeeded("reference_content", "warning", "sanitized", resource, value, result.flags());
        return result;
    }

    public Inspection processModelOutput(String value, String resource) {
        Inspection result = inspect(value, false);
        if (!properties.getContent().isRedactOutput()) result = new Inspection(value, result.flags(), false);
        auditIfNeeded("model_output", "warning", "redacted", resource, value, result.flags());
        return result;
    }

    public String redactForAudit(String value) { return inspect(value, false).sanitized(); }

    private Inspection inspect(String value, boolean userInput) {
        if (value == null || !properties.getContent().isEnabled()) return new Inspection(value, List.of(), false);
        Set<String> flags = new LinkedHashSet<>();
        String sanitized = value;
        sanitized = replace(PHONE, sanitized, "[手机号已脱敏]", "PHONE", flags);
        sanitized = replace(ID_CARD, sanitized, "[身份证号已脱敏]", "ID_CARD", flags);
        sanitized = replace(EMAIL, sanitized, "[邮箱已脱敏]", "EMAIL", flags);
        sanitized = replace(BANK_CARD, sanitized, "[银行卡号已脱敏]", "BANK_CARD", flags);
        boolean injection = false;
        for (Pattern pattern : INJECTION) {
            if (pattern.matcher(value).find()) {
                injection = true; flags.add("PROMPT_INJECTION");
                if (!userInput || !properties.getContent().isBlockPromptInjection()) {
                    sanitized = pattern.matcher(sanitized).replaceAll("[疑似提示注入内容已移除]");
                }
            }
        }
        if (!properties.getContent().isRedactBeforeModel() && userInput) sanitized = value;
        return new Inspection(sanitized, List.copyOf(flags), injection);
    }

    private String replace(Pattern pattern, String input, String replacement, String flag, Set<String> flags) {
        if (pattern.matcher(input).find()) { flags.add(flag); return pattern.matcher(input).replaceAll(replacement); }
        return input;
    }

    private void auditIfNeeded(String type, String severity, String action, String resource,
                               String raw, List<String> flags) {
        if (!properties.getContent().isAudit() || flags.isEmpty()) return;
        auditService.record(type, severity, action, resource, hash(raw), flags,
            Map.of("length", raw == null ? 0 : raw.length()));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public record Inspection(String sanitized, List<String> flags, boolean blocked) {}
}
