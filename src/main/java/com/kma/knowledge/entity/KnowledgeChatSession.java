package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问答会话
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@TableName("knowledge_chat_session")
public class KnowledgeChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long sessionId;

    private Long userId;

    private String spaceCode;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



