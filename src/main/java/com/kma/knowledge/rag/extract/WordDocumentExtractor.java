package com.kma.knowledge.rag.extract;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.ooxml.POIXMLDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

/**
 * Word 文档文本抽取器（支持 .docx / .doc）
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class WordDocumentExtractor implements DocumentExtractor {

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (
            mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || mimeType.equals("application/msword")
                || mimeType.equals("application/vnd.ms-word")
        );
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        } catch (Exception e) {
            // 回退到通用提取器
            try {
                return ExtractorFactory.createExtractor(new ByteArrayInputStream(bytes)).getText();
            } catch (Exception ex) {
                throw new IOException("Word 文档解析失败", ex);
            }
        }
    }
}



