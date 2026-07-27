package com.kma.knowledge.rag.chunk;

import java.util.List;

/**
 * 文本分块策略
 *
 * @author party
 * @date 2026/06/30
 */
public interface ChunkStrategy {

    /**
     * 策略编码
     */
    String code();

    /**
     * 将文本切分为多个块
     */
    List<String> split(String text, ChunkOptions options);
}



