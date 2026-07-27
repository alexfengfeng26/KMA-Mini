package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 投喂任务视图对象
 *
 * @author party
 * @date 2026/07/02
 */
@Data
@Schema(name = "FeedTaskVO", description = "FeedTaskVO 数据模型")
public class FeedTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long taskId;

    private String sourceType;

    private Long sourceId;

    private Long sourceVersionId;

    private String spaceCode;

    private String status;

    private Integer retryCount;

    private Integer maxRetry;

    private LocalDateTime nextExecuteTime;

    private String errorMessage;

    private String meta;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



