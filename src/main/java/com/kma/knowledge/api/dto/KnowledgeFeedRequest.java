package com.kma.knowledge.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文本投喂请求（跨模块契约）。
 */
@Data
public class KnowledgeFeedRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 空间编码 */
    private String spaceCode;

    /** 文档标题 */
    private String title;

    /** 来源标签 */
    private String sourceTag;

    /** 外部引用 */
    private String externalRef;

    /** 文档内容 */
    private String content;

    /** JSON 字符串：业务自定义元数据 */
    private String meta;
}



