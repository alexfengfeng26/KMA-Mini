package com.kma.knowledge.health;

import com.kma.knowledge.client.embedding.EmbeddingClient;
import com.kma.knowledge.client.embedding.EmbeddingClientFactory;
import com.kma.knowledge.client.llm.LlmClient;
import com.kma.knowledge.client.llm.LlmClientFactory;
import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识库健康检查单元测试
 *
 * @author party
 * @date 2026/07/02
 */
class KnowledgeHealthIndicatorTest {

    @Test
    void shouldReportUpWhenAllHealthy() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);
        when(connection.getCatalog()).thenReturn("kma");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(true);

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getStorage().setPath("upload/knowledge");

        EmbeddingClientFactory embeddingFactory = mock(EmbeddingClientFactory.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingFactory.get("zhipu")).thenReturn(embeddingClient);
        when(embeddingClient.ping()).thenReturn(true);

        LlmClientFactory llmFactory = mock(LlmClientFactory.class);
        LlmClient llmClient = mock(LlmClient.class);
        when(llmFactory.get("deepseek")).thenReturn(llmClient);
        when(llmClient.ping()).thenReturn(true);

        KnowledgeHealthIndicator indicator = new KnowledgeHealthIndicator(dataSource, properties);

        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("kma", health.getDetails().get("database"));
    }

    @Test
    void shouldReportDownWhenPgvectorMissing() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(false);

        KnowledgeHealthIndicator indicator = new KnowledgeHealthIndicator(dataSource, new KnowledgeProperties());

        Health health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void shouldReportDownWhenEmbeddingUnavailable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(true);

        KnowledgeProperties properties = new KnowledgeProperties();
        EmbeddingClientFactory embeddingFactory = mock(EmbeddingClientFactory.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingFactory.get("zhipu")).thenReturn(embeddingClient);
        when(embeddingClient.ping()).thenReturn(false);

        LlmClientFactory llmFactory = mock(LlmClientFactory.class);
        LlmClient llmClient = mock(LlmClient.class);
        when(llmFactory.get("deepseek")).thenReturn(llmClient);
        when(llmClient.ping()).thenReturn(true);

        ModelDependenciesHealthIndicator indicator = new ModelDependenciesHealthIndicator(
                embeddingFactory, llmFactory, properties);

        Health health = indicator.health();
        assertEquals(ModelDependenciesHealthIndicator.DEGRADED, health.getStatus());
    }
}



