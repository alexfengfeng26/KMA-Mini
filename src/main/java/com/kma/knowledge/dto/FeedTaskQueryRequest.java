package com.kma.knowledge.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 投喂任务分页查询请求
 *
 * @author party
 * @date 2026/07/02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "FeedTaskQueryRequest", description = "FeedTaskQueryRequest 数据模型")
public class FeedTaskQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 业务来源：course / news / file
     */
    private String sourceType;

    /**
     * 目标空间编码
     */
    private String spaceCode;

    /**
     * 任务状态
     */
    private String status;
}



