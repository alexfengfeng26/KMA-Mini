package com.kma.knowledge.rag.prompt;

import org.springframework.stereotype.Component;

/** Conservative tokenizer-independent budget estimator for mixed Chinese/English prompts. */
@Component
public class HeuristicTokenCounter implements TokenCounter {
    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) return 0;
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp < 128 && (Character.isLetterOrDigit(cp) || cp == '_' || cp == '-')) {
                asciiRun++;
            } else {
                if (asciiRun > 0) tokens += (asciiRun + 3) / 4;
                asciiRun = 0;
                if (!Character.isWhitespace(cp)) tokens++;
            }
        }
        if (asciiRun > 0) tokens += (asciiRun + 3) / 4;
        return tokens;
    }

    @Override
    public String truncate(String text, int maxTokens) {
        if (text == null || maxTokens <= 0) return "";
        if (count(text) <= maxTokens) return text;
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (count(text.substring(0, mid)) <= maxTokens) low = mid;
            else high = mid - 1;
        }
        if (low > 0 && low < text.length() && Character.isHighSurrogate(text.charAt(low - 1))) low--;
        return text.substring(0, low);
    }
}
