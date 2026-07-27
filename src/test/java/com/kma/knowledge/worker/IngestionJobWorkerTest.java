package com.kma.knowledge.worker;

import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.entity.KnowledgeIngestionJob;
import com.kma.knowledge.mapper.KnowledgeIngestionJobMapper;
import com.kma.knowledge.rag.extract.NonRetryableIngestionException;
import com.kma.knowledge.rag.pipeline.IngestionPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionJobWorkerTest {
    @Mock private KnowledgeIngestionJobMapper jobMapper;
    @Mock private IngestionPipeline pipeline;

    private KnowledgeProperties properties;
    private IngestionJobWorker worker;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        properties.getIngestion().setWorkerEnabled(true);
        properties.getIngestion().setBatchSize(4);
        properties.getIngestion().setLeaseSeconds(30);
        properties.getIngestion().setInitialRetrySeconds(1);
        properties.getIngestion().setMaxRetrySeconds(10);
        worker = new IngestionJobWorker(jobMapper, pipeline, properties);
    }

    @Test
    void executesClaimedJobsGlobally() {
        KnowledgeIngestionJob job = job(11L, 101L, 0, 3);
        when(jobMapper.claimBatch(anyString(), any(LocalDateTime.class), eq(4)))
            .thenReturn(List.of(job));
        doNothing().when(pipeline).run(101L);

        worker.poll();

        verify(jobMapper).markSucceeded(eq(11L), anyString());
    }

    @Test
    void retriesTransientFailureWithBackoff() {
        KnowledgeIngestionJob job = job(12L, 102L, 0, 3);
        when(jobMapper.claimBatch(anyString(), any(), anyInt())).thenReturn(List.of(job));
        doThrow(new IllegalStateException("temporary model failure")).when(pipeline).run(102L);

        worker.poll();

        verify(jobMapper).markRetry(eq(12L), anyString(), eq(1),
            any(LocalDateTime.class), eq("temporary model failure"));
        verify(jobMapper, never()).markDead(any(), anyString(), anyInt(), any());
    }

    @Test
    void sendsNonRetryableFailureToDeadLetter() throws Exception {
        KnowledgeIngestionJob job = job(13L, 103L, 0, 3);
        when(jobMapper.claimBatch(anyString(), any(), anyInt())).thenReturn(List.of(job));
        doThrow(new NonRetryableIngestionException("invalid embedding dimension")).when(pipeline).run(103L);

        worker.poll();

        verify(jobMapper).markDead(eq(13L), anyString(), eq(1),
            eq("invalid embedding dimension"));
        verify(jobMapper, never()).markRetry(any(), anyString(), anyInt(), any(), any());
    }

    @Test
    void nextScheduledPollRecoversAfterPostgresIsTemporarilyUnavailable() {
        KnowledgeIngestionJob job = job(14L, 104L, 0, 3);
        when(jobMapper.claimBatch(anyString(), any(), anyInt()))
            .thenThrow(new DataAccessResourceFailureException("postgres unavailable"))
            .thenReturn(List.of(job));
        doNothing().when(pipeline).run(104L);

        assertThatThrownBy(worker::poll).isInstanceOf(DataAccessResourceFailureException.class);
        worker.poll();

        verify(jobMapper).markSucceeded(eq(14L), anyString());
    }

    @Test
    void doesNothingWhenWorkerRoleIsDisabled() {
        properties.getIngestion().setWorkerEnabled(false);

        worker.poll();

        verify(jobMapper, never()).claimBatch(anyString(), any(), anyInt());
    }

    private KnowledgeIngestionJob job(long jobId, long docId, int retryCount, int maxRetry) {
        KnowledgeIngestionJob job = new KnowledgeIngestionJob();
        job.setJobId(jobId);
        job.setDocId(docId);
        job.setRetryCount(retryCount);
        job.setMaxRetry(maxRetry);
        return job;
    }
}
