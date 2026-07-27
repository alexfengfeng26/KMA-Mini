package com.kma.knowledge.spi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 外部知识来源适配器注册表
 * <p>
 * 作为 SPI 扩展点保留；接入系统实现 {@link IngestionAdapter} 后注册到本表，
 * 用于把旧文件库、其他业务表的数据批量桥接到独立知识库。
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class IngestionAdapterRegistry {

    private final Map<String, IngestionAdapter> adapterMap;

    public IngestionAdapterRegistry(List<IngestionAdapter> adapters) {
        this.adapterMap = adapters.stream()
            .collect(Collectors.toMap(IngestionAdapter::sourceType, a -> a));
    }

    /**
     * 根据来源类型获取适配器
     */
    public Optional<IngestionAdapter> get(String sourceType) {
        return Optional.ofNullable(adapterMap.get(sourceType));
    }

    /**
     * 判断是否支持指定来源类型
     */
    public boolean supports(String sourceType) {
        return adapterMap.containsKey(sourceType);
    }

    /**
     * 获取所有已注册来源类型
     */
    public List<String> listSourceTypes() {
        return List.copyOf(adapterMap.keySet());
    }
}



