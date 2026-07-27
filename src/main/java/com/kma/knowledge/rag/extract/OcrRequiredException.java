package com.kma.knowledge.rag.extract;

public class OcrRequiredException extends NonRetryableIngestionException {
    public OcrRequiredException(String message) {
        super(message);
    }
}
