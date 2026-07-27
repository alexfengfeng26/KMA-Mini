package com.kma.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.security.KmaPrincipal;
import com.kma.common.security.KmaIdentityContext;
import com.kma.knowledge.client.llm.LlmChatResponse;
import com.kma.knowledge.client.llm.LlmClient;
import com.kma.knowledge.client.llm.LlmClientFactory;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.QAResult;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import com.kma.knowledge.rag.prompt.PromptAssembler;
import com.kma.knowledge.service.KnowledgeCallLogService;
import com.kma.knowledge.service.KnowledgeChatSessionService;
import com.kma.knowledge.service.KnowledgeRetrieveService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库问答服务单元测试
 *
 * @author party
 * @date 2026/07/02
 */
class KnowledgeQAServiceTest {

    @Mock
    private KnowledgeRetrieveService retrieveService;
    @Mock
    private PromptAssembler promptAssembler;
    @Mock
    private LlmClientFactory llmClientFactory;
    @Mock
    private KnowledgeProperties properties;
    @Mock
    private KnowledgeSpaceMapper spaceMapper;
    @Mock
    private KnowledgeCallLogService callLogService;
    @Mock
    private KnowledgeChatSessionService chatSessionService;
    @Mock
    private KnowledgeSpaceAclService aclService;
    @Mock
    private RagMetricsRecorder metricsRecorder;
    @Mock
    private LlmClient llmClient;

    private KnowledgeQAServiceImpl qaService;
    private ObjectMapper objectMapper;
    private AutoCloseable closeable;
    private MockedStatic<KmaIdentityContext> identityContextMock;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        qaService = new KnowledgeQAServiceImpl(
                retrieveService,
                promptAssembler,
                llmClientFactory,
                properties,
                spaceMapper,
                callLogService,
                chatSessionService,
                aclService,
                metricsRecorder,
                objectMapper);
        identityContextMock = Mockito.mockStatic(KmaIdentityContext.class);

        KnowledgeProperties.LlmProperties llmProps = new KnowledgeProperties.LlmProperties();
        llmProps.setDefaultProvider("deepseek");
        llmProps.setModel("deepseek-chat");
        when(properties.getLlm()).thenReturn(llmProps);
        KnowledgeProperties.RagProperties ragProps = new KnowledgeProperties.RagProperties();
        when(properties.getRag()).thenReturn(ragProps);
        when(llmClientFactory.get("deepseek")).thenReturn(llmClient);
        when(promptAssembler.getSystemPrompt()).thenReturn("system");
        when(promptAssembler.buildPromptWithHistory(any(), any(), any())).thenReturn("prompt");
    }

    @AfterEach
    void tearDown() throws Exception {
        identityContextMock.close();
        closeable.close();
    }

    @Test
    void shouldReturnAnswerWithCitations() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace());
        ChunkHitVO hit = buildHit(1L, "content");
        when(retrieveService.retrieveChunks(any())).thenReturn(List.of(hit));
        when(chatSessionService.createOrGetSession(any(), any(), any(), any())).thenReturn(10L);
        when(chatSessionService.getRecentHistory(10L, 6)).thenReturn(Collections.emptyList());

        LlmChatResponse response = new LlmChatResponse();
        response.setContent("answer");
        response.setModel("deepseek-chat");
        response.setPromptTokens(10);
        response.setCompletionTokens(5);
        when(llmClient.chat(any())).thenReturn(response);

        KmaPrincipal user = new KmaPrincipal();
        user.setUserId(1L);
        user.setUsername("admin");
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::getUserId).thenReturn(1L);
        identityContextMock.when(KmaIdentityContext::getUsername).thenReturn("admin");

        QARequest request = new QARequest();
        request.setSpaceCode("space");
        request.setQuery("问题");

        QAResult ApiResult = qaService.answer(request);

        assertEquals("answer", ApiResult.getAnswer());
        assertEquals(1, ApiResult.getCitations().size());
        assertEquals(10, ApiResult.getPromptTokens());
        assertEquals(5, ApiResult.getCompletionTokens());
        assertEquals(10L, ApiResult.getSessionId());
        verify(aclService, times(2)).assertReadAccess("space");
    }

    @Test
    void shouldRefuseWhenNoEvidence() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace());
        when(retrieveService.retrieveChunks(any())).thenReturn(Collections.emptyList());
        when(chatSessionService.createOrGetSession(any(), any(), any(), any())).thenReturn(10L);
        when(chatSessionService.getRecentHistory(10L, 6)).thenReturn(Collections.emptyList());

        KmaPrincipal user = new KmaPrincipal();
        user.setUserId(1L);
        user.setUsername("admin");
        identityContextMock.when(KmaIdentityContext::getLoginUser).thenReturn(user);
        identityContextMock.when(KmaIdentityContext::getUserId).thenReturn(1L);
        identityContextMock.when(KmaIdentityContext::getUsername).thenReturn("admin");

        QARequest request = new QARequest();
        request.setSpaceCode("space");
        request.setQuery("问题");

        QAResult ApiResult = qaService.answer(request);

        assertNotNull(ApiResult.getAnswer());
        assertEquals(false, ApiResult.getAnswered());
        assertEquals("NO_EVIDENCE", ApiResult.getReason());
        verify(aclService, times(2)).assertReadAccess("space");
    }

    @Test
    void shouldNotExposeRetrievedCitationsWhenAclIsRevokedDuringRetrieval() {
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(buildSpace());
        when(retrieveService.retrieveChunks(any())).thenReturn(List.of(buildHit(1L, "secret")));
        // 第一次校验允许，第二次校验模拟检索期间 ACL 被撤销。
        org.mockito.Mockito.doNothing()
            .doThrow(new org.springframework.security.access.AccessDeniedException("revoked"))
            .when(aclService).assertReadAccess("space");

        QARequest request = new QARequest();
        request.setSpaceCode("space");
        request.setQuery("问题");

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.security.access.AccessDeniedException.class,
            () -> qaService.answer(request));
        verify(aclService, times(2)).assertReadAccess("space");
        verify(llmClient, never()).chat(any());
        verify(chatSessionService, never()).saveMessage(any(), any(), any(), any());
    }

    private KnowledgeSpace buildSpace() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(1L);
        space.setSpaceCode("space");
        space.setDefaultTopK(5);
        space.setScoreThreshold(new BigDecimal("0.35"));
        return space;
    }

    private ChunkHitVO buildHit(Long chunkId, String content) {
        ChunkHitVO hit = new ChunkHitVO();
        hit.setChunkId(chunkId);
        hit.setDocId(chunkId);
        hit.setContent(content);
        return hit;
    }
}




