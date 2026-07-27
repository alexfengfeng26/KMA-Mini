package com.kma.knowledge.service.impl;

import com.kma.common.exception.KmaException;
import com.kma.knowledge.entity.KnowledgeChatMessage;
import com.kma.knowledge.entity.KnowledgeChatSession;
import com.kma.knowledge.mapper.KnowledgeChatMessageMapper;
import com.kma.knowledge.mapper.KnowledgeChatSessionMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

class KnowledgeChatSessionServiceImplTest {

    @Test
    void createOrGetSessionShouldRejectForeignSession() {
        KnowledgeChatSessionMapper sessionMapper = Mockito.mock(KnowledgeChatSessionMapper.class);
        KnowledgeChatMessageMapper messageMapper = Mockito.mock(KnowledgeChatMessageMapper.class);
        KnowledgeChatSessionServiceImpl service = new KnowledgeChatSessionServiceImpl(sessionMapper, messageMapper);

        KnowledgeChatSession session = new KnowledgeChatSession();
        session.setSessionId(9L);
        session.setUserId(2L);
        Mockito.when(sessionMapper.selectById(9L)).thenReturn(session);

        KmaException ex = Assertions.assertThrows(KmaException.class,
            () -> service.createOrGetSession(1L, "meeting_minutes", "test", 9L));
        Assertions.assertEquals("无权限访问该会话", ex.getMessage());
    }

    @Test
    void createOrGetSessionShouldReuseOwnedSession() {
        KnowledgeChatSessionMapper sessionMapper = Mockito.mock(KnowledgeChatSessionMapper.class);
        KnowledgeChatMessageMapper messageMapper = Mockito.mock(KnowledgeChatMessageMapper.class);
        KnowledgeChatSessionServiceImpl service = new KnowledgeChatSessionServiceImpl(sessionMapper, messageMapper);

        KnowledgeChatSession session = new KnowledgeChatSession();
        session.setSessionId(9L);
        session.setUserId(1L);
        session.setSpaceCode("meeting_minutes");
        Mockito.when(sessionMapper.selectById(9L)).thenReturn(session);

        Long sessionId = service.createOrGetSession(1L, "meeting_minutes", "test", 9L);
        Assertions.assertEquals(9L, sessionId);
        Mockito.verify(sessionMapper).updateById(
            Mockito.argThat((KnowledgeChatSession item) -> item.getUpdateTime() != null));
    }

    @Test
    void listMessagesShouldRejectForeignSession() {
        KnowledgeChatSessionMapper sessionMapper = Mockito.mock(KnowledgeChatSessionMapper.class);
        KnowledgeChatMessageMapper messageMapper = Mockito.mock(KnowledgeChatMessageMapper.class);
        KnowledgeChatSessionServiceImpl service = new KnowledgeChatSessionServiceImpl(sessionMapper, messageMapper);

        KnowledgeChatSession session = new KnowledgeChatSession();
        session.setSessionId(5L);
        session.setUserId(8L);
        Mockito.when(sessionMapper.selectById(5L)).thenReturn(session);

        KmaException ex = Assertions.assertThrows(KmaException.class,
            () -> service.listMessages(1L, 5L));
        Assertions.assertEquals("无权限访问该会话", ex.getMessage());
    }

    @Test
    void listMessagesShouldReturnChronologicalMessagesForOwner() {
        KnowledgeChatSessionMapper sessionMapper = Mockito.mock(KnowledgeChatSessionMapper.class);
        KnowledgeChatMessageMapper messageMapper = Mockito.mock(KnowledgeChatMessageMapper.class);
        KnowledgeChatSessionServiceImpl service = new KnowledgeChatSessionServiceImpl(sessionMapper, messageMapper);

        KnowledgeChatSession session = new KnowledgeChatSession();
        session.setSessionId(5L);
        session.setUserId(1L);
        Mockito.when(sessionMapper.selectById(5L)).thenReturn(session);

        KnowledgeChatMessage message = new KnowledgeChatMessage();
        message.setMessageId(1L);
        message.setSessionId(5L);
        message.setRole("assistant");
        message.setContent("answer");
        message.setCreateTime(LocalDateTime.now());
        Mockito.when(messageMapper.selectList(Mockito.any())).thenReturn(List.of(message));

        List<KnowledgeChatMessage> ApiResult = service.listMessages(1L, 5L);
        Assertions.assertEquals(1, ApiResult.size());
        Assertions.assertEquals("answer", ApiResult.get(0).getContent());
    }
}



