package com.kma.knowledge.rag.extract;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.kma.knowledge.config.KnowledgeProperties;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * PDF 文档文本抽取器
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
@RequiredArgsConstructor
public class PdfDocumentExtractor implements DocumentExtractor {

    private final KnowledgeProperties properties;
    private final List<OcrProvider> ocrProviders;

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (
            mimeType.equals("application/pdf")
                || mimeType.equals("application/x-pdf")
        );
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        byte[] content = inputStream.readAllBytes();
        try (PDDocument document = PDDocument.load(content)) {
            if (document.getNumberOfPages() > properties.getDocument().getMaxPages()) {
                throw new NonRetryableIngestionException("PDF 页数超过上限: " + document.getNumberOfPages());
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                for (OcrProvider provider : ocrProviders) {
                    if (provider.available() && provider.supports("application/pdf")) {
                        return provider.extract(content, "application/pdf");
                    }
                }
                throw new OcrRequiredException("PDF 未抽取到文本，可能是扫描件，需要配置 OCR Provider");
            }
            return text;
        }
    }
}



