package com.kma.knowledge.rag.extract;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class ImageDocumentExtractor implements DocumentExtractor {
    private final List<OcrProvider> providers;

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        byte[] content = inputStream.readAllBytes();
        for (OcrProvider provider : providers) {
            if (provider.available() && provider.supports("image/*")) {
                return provider.extract(content, "image/*");
            }
        }
        throw new OcrRequiredException("图片文档需要配置 OCR Provider");
    }
}
