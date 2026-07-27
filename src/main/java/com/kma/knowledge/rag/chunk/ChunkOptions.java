package com.kma.knowledge.rag.chunk;

import lombok.Data;

/**
 * 分块选项
 *
 * @author party
 * @date 2026/06/30
 */
@Data
public class ChunkOptions {

    /**
     * 每块目标字符数
     */
    private int chunkSize = 512;

    /**
     * 块间重叠字符数
     */
    private int overlap = 50;

    /**
     * 分隔符
     */
    private String separator = "\n";
}



