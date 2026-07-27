package com.kma.knowledge;

import com.kma.knowledge.entity.KnowledgeChatMessage;
import com.kma.knowledge.mapper.KnowledgeChatMessageMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("local-pg")
@EnabledIfSystemProperty(named = "kma.local.pg.it", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class KnowledgeLocalChatMessageIntegrationTest {
    private static final String URL = required("KMA_IT_DB_URL");
    private static final String USERNAME = required("KMA_IT_DB_USERNAME");
    private static final String PASSWORD = required("KMA_IT_DB_PASSWORD");

    @Autowired KnowledgeChatMessageMapper messageMapper;
    @Autowired @Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (!databaseName(URL).endsWith("_test")) {
            throw new IllegalStateException("聊天 JSONB 集成测试只允许使用 *_test 数据库");
        }
        registry.add("knowledge.datasource.jdbc-url", () -> URL);
        registry.add("knowledge.datasource.username", () -> USERNAME);
        registry.add("knowledge.datasource.password", () -> PASSWORD);
        registry.add("knowledge.ingestion.worker-enabled", () -> false);
        registry.add("knowledge.feed.enabled", () -> false);
        registry.add("knowledge.governance.enabled", () -> false);
        registry.add("knowledge.embedding.rebuild-worker-enabled", () -> false);
    }

    @Test
    void messageCitationsRoundTripAsJsonb() {
        Long sessionId = jdbc.queryForObject("""
            INSERT INTO knowledge_chat_session(user_id,space_code,title)
            VALUES (NULL,'default','jsonb mapping integration test')
            RETURNING session_id
            """, Long.class);
        try {
            KnowledgeChatMessage message = new KnowledgeChatMessage();
            message.setSessionId(sessionId);
            message.setRole("assistant");
            message.setContent("answer");
            message.setCitations("[{\"externalRef\":\"doc-1\",\"chunkIndex\":0}]");
            message.setCreateTime(LocalDateTime.now());

            messageMapper.insert(message);

            assertThat(jdbc.queryForObject("""
                SELECT jsonb_typeof(citations) FROM knowledge_chat_message WHERE message_id=?
                """, String.class, message.getMessageId())).isEqualTo("array");
            KnowledgeChatMessage saved = messageMapper.selectById(message.getMessageId());
            assertThat(saved.getCitations()).contains("\"externalRef\"").contains("\"doc-1\"");
        } finally {
            jdbc.update("DELETE FROM knowledge_chat_session WHERE session_id=?", sessionId);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量 " + name);
        return value;
    }

    private static String databaseName(String jdbcUrl) {
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        return uri.getPath().substring(1);
    }
}
