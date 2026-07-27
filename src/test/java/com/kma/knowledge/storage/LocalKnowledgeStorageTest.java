package com.kma.knowledge.storage;

import com.kma.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalKnowledgeStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesOpensAndDeletesOnlyInsideConfiguredRoot() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getStorage().setPath(tempDir.toString());
        LocalKnowledgeStorage storage = new LocalKnowledgeStorage(properties);

        String location = storage.store("party_docs", "policy.txt",
            new ByteArrayInputStream("党建知识".getBytes(StandardCharsets.UTF_8)));

        assertThat(Path.of(location)).startsWith(tempDir);
        try (var input = storage.open(location)) {
            assertThat(input.readAllBytes()).isEqualTo("党建知识".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(storage.inspect(location).checksumAlgorithm()).isEqualTo("SHA-256");
        assertThat(storage.list(10)).extracting(StorageObjectMetadata::location).contains(location);
        storage.delete(location);
        assertThat(Path.of(location)).doesNotExist();
    }

    @Test
    void rejectsTraversalAndLocationsOutsideConfiguredRoot() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getStorage().setPath(tempDir.resolve("storage").toString());
        LocalKnowledgeStorage storage = new LocalKnowledgeStorage(properties);
        Path outside = Files.writeString(tempDir.resolve("outside.txt"), "secret");

        assertThatThrownBy(() -> storage.store("../outside", "a.txt", ByteArrayInputStream.nullInputStream()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.open(outside.toString()))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("非法本地存储位置");
        assertThatThrownBy(() -> storage.delete(outside.toString()))
            .isInstanceOf(IOException.class);
        assertThat(outside).exists();
    }
}
