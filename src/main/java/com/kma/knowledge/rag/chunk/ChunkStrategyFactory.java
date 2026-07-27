package com.kma.knowledge.rag.chunk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分块策略工厂
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class ChunkStrategyFactory {

    private final Map<String, ChunkStrategy> strategyMap;

    public ChunkStrategyFactory(List<ChunkStrategy> strategies) {
        this.strategyMap = strategies.stream()
            .collect(Collectors.toMap(ChunkStrategy::code, s -> s));
    }

    public ChunkStrategy get(String code) {
        ChunkStrategy strategy = strategyMap.get(code);
        return strategy != null ? strategy : strategyMap.get("fixed_size");
    }
}



