package com.kma.knowledge.rag.extract;

import java.io.IOException;

public interface OcrProvider {
    boolean available();
    boolean supports(String mimeType);
    String extract(byte[] content, String mimeType) throws IOException;
}
