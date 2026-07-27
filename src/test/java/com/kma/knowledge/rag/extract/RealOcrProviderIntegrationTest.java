package com.kma.knowledge.rag.extract;

import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "kma.ocr.it", matches = "true")
class RealOcrProviderIntegrationTest {

    @Test
    void recognizesConfiguredSampleAgainstRealProvider() throws Exception {
        Path sample = Path.of(required("kma.ocr.sample"));
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getOcr().setBaseUrl(required("kma.ocr.base-url"));
        properties.getOcr().setEndpoint(System.getProperty("kma.ocr.endpoint", "/api/v1/ocr"));
        properties.getOcr().setApiKey(System.getProperty("kma.ocr.api-key"));

        String text = new HttpOcrProvider(properties).extract(Files.readAllBytes(sample),
            System.getProperty("kma.ocr.mime-type", "image/png"));

        assertThat(text).isNotBlank();
        String expected = System.getProperty("kma.ocr.expected-text");
        if (expected != null && !expected.isBlank()) assertThat(text).contains(expected);
    }

    private String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少系统属性: " + name);
        return value;
    }
}
