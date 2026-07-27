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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * party-knowledge 的 pgvector 集成测试骨架。
 *
 * <p>使用 Testcontainers 启动带 pgvector 扩展的 PostgreSQL 16 容器，验证
 * {@code vector} 与 {@code jsonb} 列的写入 / 读回链路，作为独立 AI 知识库
 * RAG 存储层的最小可运行样例。</p>
 *
 * <p>本类标记为 {@code @Tag("integration")}，根 POM 的 surefire 配置了
 * {@code <excludedGroups>integration</excludedGroups>}，因此默认 {@code mvn test}
 * 会跳过本测试——不会启动容器、也不要求本机安装 Docker。只有显式指定
 * {@code mvn test -Dgroups=integration}（CI 中的独立集成测试作业）时才会运行。</p>
 *
 * @author 架构治理
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class KnowledgePgIntegrationTest {

    /**
     * pgvector 官方镜像（基于 postgres:pg16），需声明为 postgres 的兼容替代镜像，
     * 否则 {@link PostgreSQLContainer} 会因镜像名非 "postgres" 而拒绝启动。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    /**
     * 建扩展 → 建表（vector(3) + jsonb）→ 插入一行 → 查回并断言。
     *
     * @throws Exception JDBC 访问异常
     */
    @Test
    void pgvectorVectorAndJsonbRoundTrip() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = conn.createStatement()) {

            // 启用 pgvector 扩展
            st.execute("CREATE EXTENSION IF NOT EXISTS vector");

            // 建立带 vector(3) 与 jsonb 列的小表
            st.execute("CREATE TABLE knowledge_chunk (" +
                    "id bigint PRIMARY KEY, " +
                    "embedding vector(3), " +
                    "metadata jsonb)");

            // 插入一行
            st.execute("INSERT INTO knowledge_chunk (id, embedding, metadata) " +
                    "VALUES (1, '[0.1,0.2,0.3]', '{\"source\":\"unit\"}')");

            // 查回并断言
            try (ResultSet rs = st.executeQuery(
                    "SELECT id, embedding::text AS embedding, metadata->>'source' AS source " +
                    "FROM knowledge_chunk WHERE id = 1")) {
                assertTrue(rs.next(), "应查到刚插入的记录");
                assertEquals(1L, rs.getLong("id"), "主键应为 1");
                assertNotNull(rs.getString("embedding"), "vector 列应可读回");
                assertEquals("unit", rs.getString("source"), "jsonb 字段 source 应可读回");
                assertFalse(rs.next(), "应仅有一行记录");
            }
        }
    }
}



