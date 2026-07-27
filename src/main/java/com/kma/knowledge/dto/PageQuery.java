package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 分页查询基类
 *
 * @author party
 * @date 2026/06/30
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Min(1)
    private Integer pageNum = 1;

    @Min(1) @Max(100)
    private Integer pageSize = 10;
}



