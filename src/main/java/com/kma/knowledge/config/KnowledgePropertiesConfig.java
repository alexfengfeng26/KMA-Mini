package com.kma.knowledge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库配置属性注册
 * <p>
 * 无条件启用，确保其他组件（如 EmbeddingClient）在知识库关闭时也能正常注入配置对象，
 * 但实际数据源和 Mapper 仍由 {@link KnowledgeDataSourceConfig} 条件控制。
 *
 * @author party
 * @date 2026/06/30
 */
@Configuration
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgePropertiesConfig {
}



