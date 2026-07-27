package com.kma.knowledge.spi;

import lombok.Data;

import java.io.Serializable;

/**
 * 外部知识来源（桥接旧库用）
 *
 * @author party
 * @date 2026/06/30
 */
@Data
public class IngestionSource implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;

    private String content;

    private String sourceTag;

    private String externalRef;

    private String mimeType;

    private String meta;
}



