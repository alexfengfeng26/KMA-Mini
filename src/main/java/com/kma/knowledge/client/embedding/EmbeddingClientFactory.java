package com.kma.knowledge.client.embedding;

import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import com.kma.knowledge.model.ResolvedModelProfile;
import com.kma.knowledge.service.ModelProfileResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Embedding 客户端工厂
 *
 * <p>
 * 支持主备自动降级：根据 {@code knowledge.embedding.fallback-providers} 配置，
 * 主模型失败时按顺序切换到备用模型。
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class EmbeddingClientFactory {

    private final Map<String, EmbeddingClient> clientMap;
    private final KnowledgeProperties properties;
    private final RagMetricsRecorder metricsRecorder;
    private final ModelProfileResolver profileResolver;
    private final ObjectMapper objectMapper;

    public EmbeddingClientFactory(List<EmbeddingClient> clients,
                                  KnowledgeProperties properties,
                                  RagMetricsRecorder metricsRecorder,
                                  ModelProfileResolver profileResolver,
                                  ObjectMapper objectMapper) {
        this.clientMap = clients.stream()
            .collect(Collectors.toMap(EmbeddingClient::provider, c -> c));
        this.properties = properties;
        this.metricsRecorder = metricsRecorder;
        this.profileResolver = profileResolver;
        this.objectMapper = objectMapper;
    }

    public EmbeddingClient getByProfile(String profileCode) {
        List<ResolvedModelProfile> profiles = profileResolver.resolveChain(profileCode, "embedding");
        Map<String, EmbeddingClient> clients = profiles.stream().collect(Collectors.toMap(
            ResolvedModelProfile::getProfileCode,
            profile -> new ProfileEmbeddingClient(profile, objectMapper),
            (left, right) -> left,
            java.util.LinkedHashMap::new));
        List<String> chain = profiles.stream().map(ResolvedModelProfile::getProfileCode).toList();
        return new FallbackEmbeddingClient(profileCode, chain, clients, metricsRecorder);
    }

    public EmbeddingClient get(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("Embedding 提供商不能为空");
        }
        List<String> chain = buildChain(provider);
        return new FallbackEmbeddingClient(provider, chain, clientMap, metricsRecorder);
    }

    private List<String> buildChain(String primary) {
        List<String> chain = new ArrayList<>();
        chain.add(primary);
        String fallback = properties.getEmbedding().getFallbackProviders();
        if (StringUtils.hasText(fallback)) {
            Arrays.stream(fallback.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(p -> !chain.contains(p))
                .forEach(chain::add);
        }
        return chain;
    }
}



