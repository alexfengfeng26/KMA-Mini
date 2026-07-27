package com.kma.knowledge.rag.extract;

import com.kma.knowledge.config.KnowledgeProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Timeout(20)
class PdfDocumentExtractorTest {

    @Test
    void delegatesScannedPdfToAvailableOcrProvider() throws Exception {
        OcrProvider provider = mock(OcrProvider.class);
        when(provider.available()).thenReturn(true);
        when(provider.supports("application/pdf")).thenReturn(true);
        when(provider.extract(any(byte[].class), eq("application/pdf"))).thenReturn("OCR result");
        PdfDocumentExtractor extractor = new PdfDocumentExtractor(properties(10), List.of(provider));

        assertThat(extractor.extract(new ByteArrayInputStream(emptyPdf(1)))).isEqualTo("OCR result");
        verify(provider).extract(any(byte[].class), eq("application/pdf"));
    }

    @Test
    void marksScannedPdfAsOcrRequiredWhenNoProviderIsConfigured() throws Exception {
        PdfDocumentExtractor extractor = new PdfDocumentExtractor(properties(10), List.of());

        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(emptyPdf(1))))
            .isInstanceOf(OcrRequiredException.class).hasMessageContaining("OCR");
    }

    @Test
    void rejectsPdfAbovePageLimitWithoutCallingOcr() throws Exception {
        PdfDocumentExtractor extractor = new PdfDocumentExtractor(properties(1), List.of());

        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(emptyPdf(2))))
            .isInstanceOf(NonRetryableIngestionException.class).hasMessageContaining("页数超过上限");
    }

    private KnowledgeProperties properties(int maxPages) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getDocument().setMaxPages(maxPages);
        return properties;
    }

    private byte[] emptyPdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
