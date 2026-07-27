package com.kma.knowledge.service;

public interface KnowledgeIngestionJobService {
    Long enqueue(Long docId, String jobType);
}
