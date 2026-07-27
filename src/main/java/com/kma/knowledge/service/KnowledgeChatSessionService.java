package com.kma.knowledge.service;

import com.kma.knowledge.entity.KnowledgeChatMessage;
import com.kma.knowledge.entity.KnowledgeChatSession;

import java.util.List;

/**
 * 问答会话服务接口
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeChatSessionService {

    /**
     * 创建或获取会话
     */
    Long createOrGetSession(Long userId, String spaceCode, String title, Long sessionId);

    /**
     * 保存消息
     */
    void saveMessage(Long sessionId, String role, String content, String citations);

    /**
     * 获取最近消息内容列表（按时间升序）
     */
    List<String> getRecentHistory(Long sessionId, int limit);

    /**
     * 查询用户的会话列表
     */
    List<KnowledgeChatSession> listUserSessions(Long userId, String spaceCode);

    /**
     * 查询会话消息
     */
    List<KnowledgeChatMessage> listMessages(Long userId, Long sessionId);
}



