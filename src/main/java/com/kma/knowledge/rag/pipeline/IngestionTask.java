package com.kma.knowledge.rag.pipeline;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档摄入任务上下文
 * <p>
 * 用于异步摄入管线内部传递任务信息，支持重试计数、来源追踪。
 *
 * @author party
 * @date 2026/06/30
 */
@Data
public class IngestionTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务唯一标识（通常使用 docId）
     */
    private Long taskId;

    /**
     * 待摄入文档 ID
     */
    private Long docId;

    /**
     * 目标知识空间编码
     */
    private String spaceCode;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 重试次数
     */
    private int attempt;

    /**
     * 最大重试次数
     */
    private int maxRetry;

    /**
     * 任务创建时间
     */
    private LocalDateTime createTime;

    public static IngestionTask of(Long docId, String spaceCode, String title) {
        IngestionTask task = new IngestionTask();
        task.setTaskId(docId);
        task.setDocId(docId);
        task.setSpaceCode(spaceCode);
        task.setTitle(title);
        task.setAttempt(0);
        task.setMaxRetry(3);
        task.setCreateTime(LocalDateTime.now());
        return task;
    }
}



