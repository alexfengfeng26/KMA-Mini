package com.kma.knowledge.rag.prompt;

public interface TokenCounter {
    int count(String text);
    String truncate(String text, int maxTokens);
}
