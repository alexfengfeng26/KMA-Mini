package com.kma.knowledge.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识文档分页查询请求
 *
 * @author party
 * @date 2026/07/01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "DocQueryRequest", description = "DocQueryRequest 数据模型")
public class DocQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 知识空间编码
     */
    private String spaceCode;

    /**
     * 文档标题（模糊）
     */
    private String title;

    /**
     * 解析状态
     */
    private String parseStatus;
}



