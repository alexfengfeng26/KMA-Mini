package com.kma.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalThemePresetResourceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkedInBuiltInThemePackagesPassTheSameSecurityPolicy() throws Exception {
        for (String themeKey : List.of("heritage-red", "governance-blue", "ink-night", "help-center", "metro-daily")) {
            String root = "portal-themes/" + themeKey + "/";
            JsonNode manifest;
            try (InputStream input = new ClassPathResource(root + "theme.json").getInputStream()) {
                manifest = objectMapper.readTree(input);
            }
            assertEquals(themeKey, manifest.path("themeKey").asText());
            Map<String, String> files = new LinkedHashMap<>();
            for (JsonNode item : manifest.path("files")) {
                String path = item.asText();
                try (InputStream input = new ClassPathResource(root + path).getInputStream()) {
                    files.put(path, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            assertTrue(files.containsKey("layout.html"));
            assertTrue(files.containsKey("pages/home.html"));
            assertTrue(files.containsKey("styles/theme.css"));
            assertEquals(List.of(), PortalThemeSecurity.validate(files, manifest));
        }
    }
}
