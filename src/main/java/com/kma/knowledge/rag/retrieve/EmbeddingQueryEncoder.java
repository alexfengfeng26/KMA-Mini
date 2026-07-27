package com.kma.knowledge.rag.retrieve;

import com.kma.knowledge.client.embedding.EmbeddingClient;
import com.kma.knowledge.client.embedding.EmbeddingClientFactory;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 查询向量化工具
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
@RequiredArgsConstructor
public class EmbeddingQueryEncoder {

    private final EmbeddingClientFactory embeddingClientFactory;
    private final RagMetricsRecorder metricsRecorder;
    private final KnowledgeDatasetMapper datasetMapper;

    public float[] encode(String query, KnowledgeSpace space) {
        long start = System.currentTimeMillis();
        KnowledgeDataset dataset = space.getDatasetId() == null ? null : datasetMapper.selectById(space.getDatasetId());
        String profileCode = dataset == null ? null : dataset.getEmbeddingProfileCode();
        EmbeddingClient client = profileCode == null || profileCode.isBlank()
            ? embeddingClientFactory.get(space.getEmbeddingProvider())
            : embeddingClientFactory.getByProfile(profileCode);
        try {
            float[] vector = client.embed(Collections.singletonList(query)).get(0);
            metricsRecorder.recordEmbeddingLatency(client.provider(), space.getEmbeddingModel(),
                System.currentTimeMillis() - start);
            return vector;
        } catch (Exception e) {
            metricsRecorder.recordEmbeddingLatency(client.provider(), space.getEmbeddingModel(),
                System.currentTimeMillis() - start);
            throw e;
        }
    }
}



