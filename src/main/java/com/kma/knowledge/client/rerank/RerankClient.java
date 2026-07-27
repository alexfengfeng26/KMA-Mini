package com.kma.knowledge.client.rerank;

import java.util.List;

/**
 * 重排序客户端抽象
 *
 * @author party
 * @date 2026/06/30
 */
public interface RerankClient {

    /**
     * 提供商编码
     */
    String provider();

    /**
     * 对候选文本与查询的相关性打分
     *
     * @return 每个候选的得分，范围建议 0~1，越高越相关
     */
    List<Double> score(String query, List<String> texts);
}



