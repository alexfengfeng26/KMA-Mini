package com.kma.knowledge.rag.extract;

import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentExtractorRegistryTest {

    @Test
    void delegatesToMatchingExtractor() throws Exception {
        DocumentExtractor extractor = mock(DocumentExtractor.class);
        when(extractor.supports("text/test")).thenReturn(true);
        when(extractor.extract(org.mockito.ArgumentMatchers.any())).thenReturn("content");
        DocumentExtractorRegistry registry = new DocumentExtractorRegistry(List.of(extractor), properties(100));

        assertThat(registry.extract(input(), "text/test")).isEqualTo("content");
    }

    @Test
    void rejectsOversizedOrUnsupportedDocumentsAsNonRetryable() throws Exception {
        DocumentExtractor extractor = mock(DocumentExtractor.class);
        when(extractor.supports("text/test")).thenReturn(true);
        when(extractor.extract(org.mockito.ArgumentMatchers.any())).thenReturn("too long");
        DocumentExtractorRegistry registry = new DocumentExtractorRegistry(List.of(extractor), properties(3));

        assertThatThrownBy(() -> registry.extract(input(), "text/test"))
            .isInstanceOf(NonRetryableIngestionException.class).hasMessageContaining("超过上限");
        assertThatThrownBy(() -> registry.extract(input(), "unknown/type"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private KnowledgeProperties properties(int maxChars) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getDocument().setMaxExtractedChars(maxChars);
        return properties;
    }

    private ByteArrayInputStream input() {
        return new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8));
    }
}
