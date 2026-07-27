package com.kma.knowledge.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface KnowledgeStorage {
    String store(String spaceCode, String filename, InputStream content) throws IOException;
    InputStream open(String location) throws IOException;
    void delete(String location) throws IOException;
    StorageObjectMetadata inspect(String location) throws IOException;
    List<StorageObjectMetadata> list(int limit) throws IOException;
}
