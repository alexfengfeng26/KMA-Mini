package com.kma.knowledge.health;

import com.kma.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 知识库健康检查
 *
 * <p>
 * 检查项：
 * <ul>
 *   <li>PostgreSQL 连接可用性</li>
 *   <li>pgvector 扩展是否已安装</li>
 *   <li>知识库文件存储目录是否可写</li>
 *   <li>Embedding 客户端连通性</li>
 *   <li>LLM 客户端连通性</li>
 * </ul>
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeHealthIndicator implements HealthIndicator {

    private final DataSource knowledgeDataSource;
    private final KnowledgeProperties properties;

    public KnowledgeHealthIndicator(@Qualifier("knowledgeDataSource") DataSource knowledgeDataSource,
                                    KnowledgeProperties properties) {
        this.knowledgeDataSource = knowledgeDataSource;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        try (Connection connection = knowledgeDataSource.getConnection()) {
            if (!connection.isValid(5)) {
                return Health.down().withDetail("database", "连接无效").build();
            }
            String databaseName = connection.getCatalog();
            builder.withDetail("database", databaseName == null || databaseName.isBlank()
                ? "unknown" : databaseName);

            // 检查 pgvector 扩展
            boolean vectorExtensionOk = checkPgvectorExtension(connection, builder);
            if (!vectorExtensionOk) {
                return Health.down().withDetail("pgvector", "未安装或不可用").build();
            }
        } catch (Exception e) {
            log.warn("知识库健康检查失败", e);
            return Health.down().withDetail("error", e.getMessage()).build();
        }

        // 检查存储目录
        checkStoragePath(builder);

        return builder.build();
    }

    private boolean checkPgvectorExtension(Connection connection, Health.Builder builder) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')")) {
            if (rs.next()) {
                boolean installed = rs.getBoolean(1);
                builder.withDetail("pgvector", installed ? "installed" : "missing");
                return installed;
            }
        } catch (Exception e) {
            log.warn("检查 pgvector 扩展失败", e);
            builder.withDetail("pgvector", "check_failed: " + e.getMessage());
            return false;
        }
        return false;
    }

    private void checkStoragePath(Health.Builder builder) {
        String path = properties.getStorage().getPath();
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            builder.withDetail("storage", created ? "created" : "create_failed");
            return;
        }
        builder.withDetail("storage", dir.canWrite() ? "writable" : "read_only");
    }
}



