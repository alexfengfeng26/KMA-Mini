package com.kma.knowledge.service.impl;

import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.entity.KnowledgeIngestionJob;
import com.kma.knowledge.mapper.KnowledgeIngestionJobMapper;
import com.kma.knowledge.service.KnowledgeIngestionJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class KnowledgeIngestionJobServiceImpl implements KnowledgeIngestionJobService {
    private final KnowledgeIngestionJobMapper jobMapper;
    private final KnowledgeProperties properties;

    @Override
    public Long enqueue(Long docId, String jobType) {
        KnowledgeIngestionJob job = jobMapper.enqueue(
            docId, jobType, properties.getIngestion().getMaxRetry());
        return job == null ? null : job.getJobId();
    }
}
