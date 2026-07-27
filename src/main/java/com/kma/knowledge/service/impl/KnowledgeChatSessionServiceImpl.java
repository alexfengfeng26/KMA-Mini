package com.kma.knowledge.service.impl;

import com.kma.knowledge.entity.KnowledgeChatMessage;
import com.kma.knowledge.entity.KnowledgeChatSession;
import com.kma.knowledge.mapper.KnowledgeChatMessageMapper;
import com.kma.knowledge.mapper.KnowledgeChatSessionMapper;
import com.kma.knowledge.service.KnowledgeChatSessionService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import com.kma.common.exception.KmaException;
import com.kma.common.result.KmaResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 问答会话服务实现
 *
 * @author party
 * @date 2026/06/30
 */
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeChatSessionServiceImpl implements KnowledgeChatSessionService {

    private final KnowledgeChatSessionMapper sessionMapper;
    private final KnowledgeChatMessageMapper messageMapper;
    private final KnowledgeSpaceAclService aclService;

    public KnowledgeChatSessionServiceImpl(KnowledgeChatSessionMapper sessionMapper,
                                           KnowledgeChatMessageMapper messageMapper) {
        this(sessionMapper, messageMapper, null);
    }

    @Autowired
    public KnowledgeChatSessionServiceImpl(KnowledgeChatSessionMapper sessionMapper,
                                           KnowledgeChatMessageMapper messageMapper,
                                           KnowledgeSpaceAclService aclService) {
        this.sessionMapper=sessionMapper; this.messageMapper=messageMapper; this.aclService=aclService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public Long createOrGetSession(Long userId, String spaceCode, String title, Long sessionId) {
        if (aclService != null) aclService.assertReadAccess(spaceCode);
        if (sessionId != null) {
            KnowledgeChatSession session = sessionMapper.selectById(sessionId);
            if (session != null) {
                if (userId != null && session.getUserId() != null && !userId.equals(session.getUserId())) {
                    throw new KmaException(KmaResultCode.PERMISSION_DENIED, "无权限访问该会话");
                }
                if (!spaceCode.equals(session.getSpaceCode())) throw new KmaException(409, "会话不属于当前知识空间");
                session.setUpdateTime(LocalDateTime.now());
                sessionMapper.updateById(session);
                return sessionId;
            }
        }
        KnowledgeChatSession session = new KnowledgeChatSession();
        session.setUserId(userId);
        session.setSpaceCode(spaceCode);
        session.setTitle(title != null && title.length() > 100 ? title.substring(0, 100) + "..." : title);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session.getSessionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void saveMessage(Long sessionId, String role, String content, String citations) {
        KnowledgeChatMessage message = new KnowledgeChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCitations(citations);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }

    @Override
    public List<String> getRecentHistory(Long sessionId, int limit) {
        List<KnowledgeChatMessage> messages = messageMapper.selectRecentBySession(sessionId, limit);
        messages.sort(Comparator.comparing(KnowledgeChatMessage::getCreateTime));
        return messages.stream()
            .map(m -> ("user".equals(m.getRole()) ? "用户：" : "助手：") + m.getContent())
            .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeChatSession> listUserSessions(Long userId, String spaceCode) {
        if (aclService != null && spaceCode != null && !spaceCode.isEmpty()) aclService.assertReadAccess(spaceCode);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeChatSession> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChatSession::getUserId, userId);
        if (spaceCode != null && !spaceCode.isEmpty()) {
            wrapper.eq(KnowledgeChatSession::getSpaceCode, spaceCode);
        }
        wrapper.orderByDesc(KnowledgeChatSession::getUpdateTime);
        List<KnowledgeChatSession> sessions = sessionMapper.selectList(wrapper);
        if (aclService == null || (spaceCode != null && !spaceCode.isEmpty())) return sessions;
        return sessions.stream().filter(session -> aclService.hasReadAccess(session.getSpaceCode())).toList();
    }

    @Override
    public List<KnowledgeChatMessage> listMessages(Long userId, Long sessionId) {
        KnowledgeChatSession session = assertSessionOwner(userId, sessionId);
        if (aclService != null) aclService.assertReadAccess(session.getSpaceCode());
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeChatMessage> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChatMessage::getSessionId, sessionId);
        wrapper.orderByAsc(KnowledgeChatMessage::getCreateTime);
        return messageMapper.selectList(wrapper);
    }

    private KnowledgeChatSession assertSessionOwner(Long userId, Long sessionId) {
        KnowledgeChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new KmaException("会话不存在");
        }
        if (userId != null && session.getUserId() != null && !userId.equals(session.getUserId())) {
            throw new KmaException(KmaResultCode.PERMISSION_DENIED, "无权限访问该会话");
        }
        return session;
    }
}



