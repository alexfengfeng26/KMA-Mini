package com.kma.knowledge.health;

import com.kma.knowledge.client.embedding.EmbeddingClientFactory;
import com.kma.knowledge.client.llm.LlmClientFactory;
import com.kma.knowledge.config.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("knowledgeModels")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class ModelDependenciesHealthIndicator implements HealthIndicator {
    public static final Status DEGRADED = new Status("DEGRADED");
    private final EmbeddingClientFactory embeddingClientFactory;
    private final LlmClientFactory llmClientFactory;
    private final KnowledgeProperties properties;

    @Override
    public Health health() {
        boolean embedding = pingEmbedding();
        boolean llm = pingLlm();
        Health.Builder builder = embedding && llm ? Health.up() : Health.status(DEGRADED);
        return builder.withDetail("embedding", embedding ? "up" : "down")
            .withDetail("llm", llm ? "up" : "down").build();
    }

    private boolean pingEmbedding() {
        try {
            return embeddingClientFactory.get(properties.getEmbedding().getDefaultProvider()).ping();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean pingLlm() {
        try {
            return llmClientFactory.get(properties.getLlm().getDefaultProvider()).ping();
        } catch (Exception ignored) {
            return false;
        }
    }
}
