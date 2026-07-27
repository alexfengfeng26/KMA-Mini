package com.kma.knowledge.rag.chunk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 段落分块策略：按段落切分后，按 chunkSize 合并相邻段落
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class ParagraphChunkStrategy implements ChunkStrategy {

    @Override
    public String code() {
        return "paragraph";
    }

    @Override
    public List<String> split(String text, ChunkOptions options) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        String[] paragraphs = text.split("\\n\\s*\\n");
        int chunkSize = Math.max(options.getChunkSize(), 100);

        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (current.length() + paragraph.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(paragraph).append("\n\n");
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        // 兜底：如果段落极大，按固定大小再切
        if (chunks.isEmpty()) {
            return new FixedSizeChunkStrategy().split(text, options);
        }
        return chunks;
    }
}



