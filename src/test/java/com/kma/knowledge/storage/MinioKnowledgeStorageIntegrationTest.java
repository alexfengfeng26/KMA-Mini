package com.kma.knowledge.storage;

import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "kma.minio.it", matches = "true")
class MinioKnowledgeStorageIntegrationTest {

    @Test
    void storesInspectsListsReadsAndDeletesRealObject() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties();
        var storage = properties.getStorage();
        storage.setEndpoint(required("kma.minio.endpoint"));
        storage.setAccessKey(required("kma.minio.access-key"));
        storage.setSecretKey(required("kma.minio.secret-key"));
        storage.setBucket(System.getProperty("kma.minio.bucket", "kma-p2-it"));
        storage.setRegion(System.getProperty("kma.minio.region"));
        MinioKnowledgeStorage minio = new MinioKnowledgeStorage(properties);
        minio.initialize();

        byte[] payload = "KMA MinIO lifecycle verification".getBytes(StandardCharsets.UTF_8);
        String location = minio.store("documents", "sample.txt", new ByteArrayInputStream(payload));
        try {
            assertThat(minio.inspect(location).sizeBytes()).isEqualTo(payload.length);
            assertThat(minio.list(100)).extracting(StorageObjectMetadata::location).contains(location);
            assertThat(minio.open(location).readAllBytes()).isEqualTo(payload);
        } finally {
            minio.delete(location);
        }
    }

    private String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少系统属性: " + name);
        return value;
    }
}
