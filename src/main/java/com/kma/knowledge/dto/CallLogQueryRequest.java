package com.kma.knowledge.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 调用日志分页查询请求
 *
 * @author party
 * @date 2026/07/02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "CallLogQueryRequest", description = "CallLogQueryRequest 数据模型")
public class CallLogQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String spaceCode;

    private String ragMode;

    private String status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}



