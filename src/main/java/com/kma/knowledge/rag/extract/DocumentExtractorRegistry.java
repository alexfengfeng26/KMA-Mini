package com.kma.knowledge.rag.extract;

import lombok.RequiredArgsConstructor;
import com.kma.knowledge.config.KnowledgeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文档抽取器注册表
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
@RequiredArgsConstructor
public class DocumentExtractorRegistry {

    private final List<DocumentExtractor> extractors;
    private final KnowledgeProperties properties;

    /**
     * 根据 MIME 类型抽取文本
     */
    public String extract(InputStream inputStream, String mimeType) throws IOException {
        for (DocumentExtractor extractor : extractors) {
            if (extractor.supports(mimeType)) {
                String text = extractor.extract(inputStream);
                if (text != null && text.length() > properties.getDocument().getMaxExtractedChars()) {
                    throw new NonRetryableIngestionException("文档抽取文本超过上限: "
                        + properties.getDocument().getMaxExtractedChars());
                }
                return text;
            }
        }
        throw new UnsupportedOperationException("不支持的文档类型: " + mimeType);
    }
}



