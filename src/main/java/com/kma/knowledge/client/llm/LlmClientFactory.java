package com.kma.knowledge.client.llm;

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
 * LLM 客户端工厂
 *
 * <p>
 * 支持主备自动降级：根据 {@code knowledge.llm.fallback-providers} 配置，
 * 主模型失败时按顺序切换到备用模型。
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class LlmClientFactory {

    private final Map<String, LlmClient> clientMap;
    private final KnowledgeProperties properties;
    private final RagMetricsRecorder metricsRecorder;
    private final ModelProfileResolver profileResolver;
    private final ObjectMapper objectMapper;

    public LlmClientFactory(List<LlmClient> clients,
                            KnowledgeProperties properties,
                            RagMetricsRecorder metricsRecorder,
                            ModelProfileResolver profileResolver,
                            ObjectMapper objectMapper) {
        this.clientMap = clients.stream()
            .collect(Collectors.toMap(LlmClient::provider, c -> c));
        this.properties = properties;
        this.metricsRecorder = metricsRecorder;
        this.profileResolver = profileResolver;
        this.objectMapper = objectMapper;
    }

    public LlmClient getDefaultOrConfigured() {
        ResolvedModelProfile profile = profileResolver.resolveDefault("llm");
        return profile == null ? get(properties.getLlm().getDefaultProvider()) : getByProfile(profile.getProfileCode());
    }

    public LlmClient getByProfile(String profileCode) {
        List<ResolvedModelProfile> profiles = profileResolver.resolveChain(profileCode, "llm");
        Map<String, LlmClient> clients = profiles.stream().collect(Collectors.toMap(
            ResolvedModelProfile::getProfileCode,
            profile -> new ProfileLlmClient(profile, objectMapper),
            (left, right) -> left,
            java.util.LinkedHashMap::new));
        List<String> chain = profiles.stream().map(ResolvedModelProfile::getProfileCode).toList();
        return new FallbackLlmClient(profileCode, chain, clients, metricsRecorder);
    }

    /** Creates a single-profile client. Probes must never silently pass through a fallback. */
    public LlmClient getSingleProfile(String profileCode) {
        return new ProfileLlmClient(profileResolver.resolve(profileCode, "llm"), objectMapper);
    }

    public LlmClient get(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("LLM 提供商不能为空");
        }
        List<String> chain = buildChain(provider);
        return new FallbackLlmClient(provider, chain, clientMap, metricsRecorder);
    }

    private List<String> buildChain(String primary) {
        List<String> chain = new ArrayList<>();
        chain.add(primary);
        String fallback = properties.getLlm().getFallbackProviders();
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



