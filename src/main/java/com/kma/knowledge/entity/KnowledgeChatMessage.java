package com.kma.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kma.knowledge.config.JsonbStringTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 问答会话消息
 *
 * @author party
 * @date 2026/06/30
 */
@Data
@TableName(value = "knowledge_chat_message", autoResultMap = true)
public class KnowledgeChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long messageId;

    private Long sessionId;

    /**
     * 角色：user / assistant
     */
    private String role;

    private String content;

    /**
     * JSONB：引用分块
     */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String citations;

    private LocalDateTime createTime;
}



