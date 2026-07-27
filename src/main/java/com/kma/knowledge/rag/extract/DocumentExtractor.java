package com.kma.knowledge.rag.extract;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档文本抽取器
 *
 * @author party
 * @date 2026/06/30
 */
public interface DocumentExtractor {

    /**
     * 是否支持该 MIME 类型
     */
    boolean supports(String mimeType);

    /**
     * 抽取纯文本
     */
    String extract(InputStream inputStream) throws IOException;
}



