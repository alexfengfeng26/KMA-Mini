package com.kma.knowledge.rag.extract;

import cn.hutool.core.io.IoUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 纯文本抽取器
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class PlainTextExtractor implements DocumentExtractor {

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (
            mimeType.startsWith("text/")
                || mimeType.contains("json")
                || mimeType.contains("xml")
                || mimeType.contains("javascript")
        );
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        return IoUtil.read(inputStream, StandardCharsets.UTF_8);
    }
}



