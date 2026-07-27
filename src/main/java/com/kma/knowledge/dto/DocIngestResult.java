package com.kma.knowledge.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档摄入结果
 *
 * @author party
 * @date 2026/06/30
 */
@Data
public class DocIngestResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long docId;

    private String title;

    private String parseStatus;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime createTime;
}



