package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库投喂任务表（本地消息表）
 *
 * @author party
 * @date 2026/07/02
 */
@Data
@TableName("knowledge_feed_task")
public class KnowledgeFeedTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long taskId;

    /**
     * 业务来源：course / news / file
     */
    private String sourceType;

    /**
     * 业务对象 ID，如 course_id / news_id / file_id
     */
    private Long sourceId;

    /**
     * 业务对象版本或子 ID（如文件版本）
     */
    private Long sourceVersionId;

    /**
     * 目标空间编码
     */
    private String spaceCode;

    /**
     * 任务状态：pending / processing / success / dead
     */
    private String status;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 下次执行时间（指数退避）
     */
    private LocalDateTime nextExecuteTime;

    /**
     * 最后一次错误信息
     */
    private String errorMessage;

    /**
     * JSON 元数据，保留原始事件信息
     */
    private String meta;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



