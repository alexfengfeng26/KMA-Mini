package com.kma.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kma.knowledge.entity.KnowledgeIngestionJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface KnowledgeIngestionJobMapper extends BaseMapper<KnowledgeIngestionJob> {

    @Select("""
        INSERT INTO knowledge_ingestion_job
            (doc_id, job_type, status, retry_count, max_retry, next_execute_time)
        VALUES
            (#{docId}, #{jobType}, 'pending', 0, #{maxRetry}, now())
        ON CONFLICT (doc_id) WHERE status IN ('pending', 'processing')
        DO UPDATE SET next_execute_time = LEAST(knowledge_ingestion_job.next_execute_time, now()),
                      update_time = now()
        RETURNING *
        """)
    KnowledgeIngestionJob enqueue(@Param("docId") Long docId,
                                  @Param("jobType") String jobType, @Param("maxRetry") int maxRetry);

    @Select("""
        WITH candidates AS (
            SELECT job_id
            FROM knowledge_ingestion_job
            WHERE next_execute_time <= now()
              AND (
                    status = 'pending'
                    OR (status = 'processing' AND (lease_until IS NULL OR lease_until < now()))
                  )
            ORDER BY next_execute_time, job_id
            FOR UPDATE SKIP LOCKED
            LIMIT #{limit}
        )
        UPDATE knowledge_ingestion_job job
        SET status = 'processing', lease_owner = #{workerId}, lease_until = #{leaseUntil}, update_time = now()
        FROM candidates
        WHERE job.job_id = candidates.job_id
        RETURNING job.*
        """)
    List<KnowledgeIngestionJob> claimBatch(@Param("workerId") String workerId,
                                           @Param("leaseUntil") LocalDateTime leaseUntil,
                                           @Param("limit") int limit);

    @Update("""
        UPDATE knowledge_ingestion_job
        SET status = 'succeeded', lease_owner = NULL, lease_until = NULL,
            error_message = NULL, update_time = now()
        WHERE job_id = #{jobId}
          AND status = 'processing' AND lease_owner = #{workerId}
        """)
    int markSucceeded(@Param("jobId") Long jobId,
                      @Param("workerId") String workerId);

    @Update("""
        UPDATE knowledge_ingestion_job
        SET status = 'pending', retry_count = #{retryCount}, next_execute_time = #{nextExecuteTime},
            lease_owner = NULL, lease_until = NULL, error_message = #{errorMessage}, update_time = now()
        WHERE job_id = #{jobId}
          AND status = 'processing' AND lease_owner = #{workerId}
        """)
    int markRetry(@Param("jobId") Long jobId,
                  @Param("workerId") String workerId, @Param("retryCount") int retryCount,
                  @Param("nextExecuteTime") LocalDateTime nextExecuteTime,
                  @Param("errorMessage") String errorMessage);

    @Update("""
        UPDATE knowledge_ingestion_job
        SET status = 'dead', retry_count = #{retryCount}, lease_owner = NULL, lease_until = NULL,
            error_message = #{errorMessage}, update_time = now()
        WHERE job_id = #{jobId}
          AND status = 'processing' AND lease_owner = #{workerId}
        """)
    int markDead(@Param("jobId") Long jobId,
                 @Param("workerId") String workerId, @Param("retryCount") int retryCount,
                 @Param("errorMessage") String errorMessage);
}
