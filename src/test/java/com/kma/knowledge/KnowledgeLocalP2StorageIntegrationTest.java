package com.kma.knowledge;

import com.kma.knowledge.storage.KnowledgeStorage;
import com.kma.knowledge.storage.StorageLifecycleService;
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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("local-pg")
@EnabledIfSystemProperty(named = "kma.local.pg.it", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class KnowledgeLocalP2StorageIntegrationTest {
    private static final String URL = required("KMA_IT_DB_URL");
    private static final String USERNAME = required("KMA_IT_DB_USERNAME");
    private static final String PASSWORD = required("KMA_IT_DB_PASSWORD");
    private static final Path STORAGE_PATH = Path.of("target", "p2-it-storage").toAbsolutePath();

    @Autowired KnowledgeStorage storage;
    @Autowired StorageLifecycleService lifecycle;
    @Autowired @Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (!databaseName(URL).endsWith("_test")) throw new IllegalStateException("存储集成测试只允许使用 *_test 数据库");
        registry.add("knowledge.datasource.jdbc-url", () -> URL);
        registry.add("knowledge.datasource.username", () -> USERNAME);
        registry.add("knowledge.datasource.password", () -> PASSWORD);
        registry.add("knowledge.storage.path", () -> STORAGE_PATH.toString());
        registry.add("knowledge.storage.orphan-grace-hours", () -> 0);
        registry.add("knowledge.storage.cleanup-fixed-delay", () -> 86_400_000);
        registry.add("knowledge.storage.reconcile-cron", () -> "0 0 0 1 1 ?");
        registry.add("knowledge.ingestion.worker-enabled", () -> false);
        registry.add("knowledge.feed.enabled", () -> false);
        registry.add("knowledge.governance.enabled", () -> false);
        registry.add("knowledge.embedding.rebuild-worker-enabled", () -> false);
    }

    @Test
    void reconcilesUnreferencedObjectAndDeletesItAfterGracePeriod() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name='knowledge_storage_object'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_constraint
            WHERE conname IN ('fk_doc_storage_object_scope','fk_storage_reference_object_scope',
                              'fk_storage_reference_doc_scope')
            """, Integer.class)).isEqualTo(3);
        AtomicReference<String> location = new AtomicReference<>();
        AtomicReference<Long> objectId = new AtomicReference<>();
        try {
            location.set(storage.store("p2it", "orphan.txt",
                new ByteArrayInputStream("orphan-object".getBytes(StandardCharsets.UTF_8))));
            var metadata = storage.inspect(location.get());
            objectId.set(lifecycle.registerStored(location.get(), metadata.sizeBytes(), metadata.checksum()));
            assertThat(lifecycle.reconcile()).containsEntry("orphan", 1);

            lifecycle.cleanupNow();

            assertThat(Files.exists(Path.of(location.get()))).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM knowledge_storage_object WHERE object_id=?",
                String.class, objectId.get())).isEqualTo("deleted");
        } finally {
            if (objectId.get() != null) jdbc.update("DELETE FROM knowledge_storage_object WHERE object_id=?", objectId.get());
            if (location.get() != null) Files.deleteIfExists(Path.of(location.get()));
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
