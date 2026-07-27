package com.kma.knowledge.client.embedding;

import java.util.List;

/**
 * 嵌入模型客户端抽象
 *
 * @author party
 * @date 2026/06/30
 */
public interface EmbeddingClient {

    /**
     * 提供商编码
     */
    String provider();

    /**
     * 向量维度
     */
    int dimension();

    /**
     * 批量嵌入
     */
    List<float[]> embed(List<String> texts);

    /**
     * 轻量连通性探测：嵌入单个文本并校验返回维度
     */
    default boolean ping() {
        try {
            List<float[]> vectors = embed(List.of("ping"));
            return vectors != null && vectors.size() == 1 && vectors.get(0) != null && vectors.get(0).length == dimension();
        } catch (Exception e) {
            return false;
        }
    }
}



