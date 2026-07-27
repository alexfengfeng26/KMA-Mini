package com.kma.knowledge;

import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("local-pg")
@EnabledIfSystemProperty(named = "kma.local.pg.it", matches = "true")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.flyway.enabled=false",
    "knowledge.ingestion.worker-enabled=false",
    "knowledge.feed.enabled=false",
    "knowledge.governance.enabled=false",
    "knowledge.embedding.rebuild-worker-enabled=false",
    "knowledge.storage.path=target/single-instance-it-storage"
})
class KnowledgeLocalSingleInstanceIntegrationTest {
    private static final String URL = required("KMA_IT_DB_URL");
    private static final String USERNAME = required("KMA_IT_DB_USERNAME");
    private static final String PASSWORD = required("KMA_IT_DB_PASSWORD");

    @Autowired private KnowledgeDocMapper docMapper;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("knowledge.datasource.jdbc-url", () -> URL);
        registry.add("knowledge.datasource.username", () -> USERNAME);
        registry.add("knowledge.datasource.password", () -> PASSWORD);
        registry.add("knowledge.datasource.maximum-pool-size", () -> 4);
        registry.add("knowledge.datasource.minimum-idle", () -> 1);
    }

    @BeforeAll
    static void migrate() {
        assertThat(databaseName(URL)).endsWith("_test");
        Flyway.configure().dataSource(URL, USERNAME, PASSWORD)
            .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void v22CreatesOneGlobalBusinessNamespace() {
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema='public' AND column_name=concat('ten','ant_id')
            """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_schema='public' AND table_name IN
              (concat('kma_','ten','ant'),concat('kma_','ten','ant_','quo','ta'),
               concat('kma_','ten','ant_daily_usage'))
            """, Integer.class)).isZero();
    }

    @Test
    void mybatisReadsDocumentsWithoutImplicitNamespaceState() {
        String suffix = UUID.randomUUID().toString();
        Long datasetId = jdbc.queryForObject("""
            INSERT INTO knowledge_dataset(name) VALUES (?)
            RETURNING dataset_id
            """, Long.class, "single-instance-" + suffix);
        Long spaceId = jdbc.queryForObject("""
            INSERT INTO knowledge_space(dataset_id,space_code,name,
                embedding_provider,embedding_model,embedding_dim)
            VALUES (?, ?, ?, 'local', 'test', 768)
            RETURNING space_id
            """, Long.class, datasetId, "single-" + suffix, "single-instance");
        Long docId = jdbc.queryForObject("""
            INSERT INTO knowledge_doc(space_id,title,parse_status,is_active)
            VALUES (?, 'single-document', 'completed', true)
            RETURNING doc_id
            """, Long.class, spaceId);

        KnowledgeDoc document = docMapper.selectById(docId);
        assertThat(document).isNotNull();
        assertThat(docMapper.selectBySpaceId(spaceId)).extracting(KnowledgeDoc::getDocId).contains(docId);
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
