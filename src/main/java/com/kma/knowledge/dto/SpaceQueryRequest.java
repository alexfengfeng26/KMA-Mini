package com.kma.knowledge.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识空间分页查询请求
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "SpaceQueryRequest", description = "SpaceQueryRequest 数据模型")
public class SpaceQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    private String spaceCode;

    private String name;

    private String status;
}



