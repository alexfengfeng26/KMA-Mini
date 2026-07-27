package com.kma.knowledge.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据集分页查询请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "DatasetQueryRequest", description = "DatasetQueryRequest 数据模型")
public class DatasetQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    private String name;

    private String status;
}



