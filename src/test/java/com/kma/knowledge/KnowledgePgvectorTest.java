package com.kma.knowledge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * pgvector 集成测试
 * <p>
 * 需要本地安装 Docker 才能运行。
 *
 * @author party
 * @date 2026/06/30
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class KnowledgePgvectorTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("party_knowledge")
        .withUsername("party")
        .withPassword("party");

    @Test
    void shouldEnablePgvectorAndQueryVector() throws Exception {
        try (Connection conn = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
                stmt.execute("CREATE TABLE test_items (id bigserial PRIMARY KEY, embedding vector(3))");
                stmt.execute("INSERT INTO test_items (embedding) VALUES ('[1,2,3]'), ('[4,5,6]')");

                try (ResultSet rs = stmt.executeQuery(
                    "SELECT id FROM test_items ORDER BY embedding <=> '[1,2,3]' LIMIT 1")) {
                    assertTrue(rs.next());
                    assertTrue(rs.getLong("id") > 0);
                }
            }
        }
    }
}



