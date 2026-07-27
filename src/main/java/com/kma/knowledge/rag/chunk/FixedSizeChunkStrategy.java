package com.kma.knowledge.rag.chunk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分块策略（带重叠）
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class FixedSizeChunkStrategy implements ChunkStrategy {

    @Override
    public String code() {
        return "fixed_size";
    }

    @Override
    public List<String> split(String text, ChunkOptions options) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int chunkSize = Math.max(options.getChunkSize(), 100);
        int overlap = Math.min(options.getOverlap(), chunkSize / 2);
        int step = chunkSize - overlap;

        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
        }
        return chunks;
    }
}



