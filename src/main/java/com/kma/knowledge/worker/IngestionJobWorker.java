package com.kma.knowledge.worker;

import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.entity.KnowledgeIngestionJob;
import com.kma.knowledge.mapper.KnowledgeIngestionJobMapper;
import com.kma.knowledge.rag.pipeline.IngestionPipeline;
import com.kma.knowledge.rag.extract.NonRetryableIngestionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge.ingestion", name = "worker-enabled", havingValue = "true")
public class IngestionJobWorker {
    private final KnowledgeIngestionJobMapper jobMapper;
    private final IngestionPipeline pipeline;
    private final KnowledgeProperties properties;
    private final String workerId = buildWorkerId();

    @Scheduled(fixedDelayString = "${knowledge.ingestion.fixed-delay:2000}")
    public void poll() {
        if (!properties.getIngestion().isWorkerEnabled()) {
            return;
        }
        List<KnowledgeIngestionJob> jobs = jobMapper.claimBatch(
            workerId,
            LocalDateTime.now().plusSeconds(properties.getIngestion().getLeaseSeconds()),
            properties.getIngestion().getBatchSize());
        for (KnowledgeIngestionJob job : jobs) {
            execute(job);
        }
    }

    private void execute(KnowledgeIngestionJob job) {
        try {
            pipeline.run(job.getDocId());
            jobMapper.markSucceeded(job.getJobId(), workerId);
        } catch (Exception ex) {
            int retryCount = job.getRetryCount() + 1;
            String message = truncate(ex.getMessage(), 1000);
            if (ex instanceof NonRetryableIngestionException || retryCount >= job.getMaxRetry()) {
                jobMapper.markDead(job.getJobId(), workerId, retryCount, message);
                log.error("文档入库任务进入死信: jobId={}, docId={}",
                    job.getJobId(), job.getDocId(), ex);
                return;
            }
            long delay = Math.min(
                properties.getIngestion().getInitialRetrySeconds() * (1L << Math.min(retryCount - 1, 20)),
                properties.getIngestion().getMaxRetrySeconds());
            jobMapper.markRetry(job.getJobId(), workerId, retryCount,
                LocalDateTime.now().plusSeconds(delay), message);
            log.warn("文档入库任务将在 {} 秒后重试: jobId={}, docId={}, error={}",
                delay, job.getJobId(), job.getDocId(), message);
        }
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "未知错误";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private static String buildWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + UUID.randomUUID();
        } catch (Exception ignored) {
            return "kma-worker:" + UUID.randomUUID();
        }
    }
}
