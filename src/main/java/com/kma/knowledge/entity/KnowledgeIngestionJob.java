package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_ingestion_job")
public class KnowledgeIngestionJob implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long jobId;
    private Long docId;
    private String jobType;
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime nextExecuteTime;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
