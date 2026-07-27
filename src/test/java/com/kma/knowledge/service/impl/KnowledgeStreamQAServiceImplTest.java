package com.kma.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.knowledge.client.llm.LlmClientFactory;
import com.kma.knowledge.client.llm.LlmClient;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import com.kma.knowledge.rag.prompt.PromptAssembler;
import com.kma.knowledge.service.KnowledgeChatSessionService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class KnowledgeStreamQAServiceImplTest {
    @Mock private KnowledgeSpaceMapper spaceMapper;
    @Mock private PromptAssembler promptAssembler;
    @Mock private LlmClientFactory llmClientFactory;
    @Mock private KnowledgeRetrieveServiceImpl retrieveService;
    @Mock private KnowledgeChatSessionService chatSessionService;
    @Mock private KnowledgeSpaceAclService aclService;
    @Mock private RagMetricsRecorder metricsRecorder;
    @Mock private ScheduledExecutorService scheduler;
    @Mock private ScheduledFuture<?> heartbeat;
    @Mock private SseEmitter emitter;
    @Mock private LlmClient llmClient;

    @Test
    @SuppressWarnings("unchecked")
    void streamsCitationsSanitizedMessagesAndDoneThroughUnifiedClient() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties();
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceCode("space");
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(space);
        ChunkHitVO hit = new ChunkHitVO();
        hit.setChunkId(1L);
        hit.setExternalRef("doc:1");
        hit.setContent("evidence");
        when(retrieveService.retrieveChunks(any())).thenReturn(List.of(hit));
        when(chatSessionService.createOrGetSession(any(), eq("space"), eq("question"), any())).thenReturn(7L);
        when(chatSessionService.getRecentHistory(7L, 6)).thenReturn(List.of("history"));
        when(promptAssembler.buildPromptWithHistory(eq("question"), any(), eq(List.of("history"))))
            .thenReturn("prompt");
        when(promptAssembler.getSystemPrompt()).thenReturn("system");
        when(llmClientFactory.getDefaultOrConfigured()).thenReturn(llmClient);
        when(llmClient.provider()).thenReturn("ollama");
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("answer-");
            consumer.accept("chunk");
            return null;
        }).when(llmClient).streamChat(any(), any());
        doReturn(heartbeat).when(scheduler)
            .scheduleAtFixedRate(any(Runnable.class), eq(15L), eq(15L), eq(TimeUnit.SECONDS));
        KnowledgeStreamQAServiceImpl service = new KnowledgeStreamQAServiceImpl(
            spaceMapper, promptAssembler, llmClientFactory, properties, retrieveService,
            chatSessionService, aclService, metricsRecorder, new ObjectMapper(), Runnable::run, scheduler);
        QARequest request = new QARequest();
        request.setSpaceCode("space");
        request.setQuery("question");
        request.setTopK(5);

        service.streamAnswer(request, emitter);

        verify(aclService, times(2)).assertReadAccess("space");
        verify(emitter, times(4)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(chatSessionService).saveMessage(7L, "user", "question", null);
        verify(chatSessionService).saveMessage(eq(7L), eq("assistant"), eq("answer-chunk"), any());
        verify(metricsRecorder).recordQaStatus("space", "success");
        verify(heartbeat).cancel(false);
    }

    @Test
    void missingSpaceReturnsErrorEventWithoutCallingModel() throws Exception {
        when(spaceMapper.selectBySpaceCode("missing")).thenReturn(null);
        doReturn(heartbeat).when(scheduler)
            .scheduleAtFixedRate(any(Runnable.class), eq(15L), eq(15L), eq(TimeUnit.SECONDS));
        KnowledgeStreamQAServiceImpl service = new KnowledgeStreamQAServiceImpl(
            spaceMapper, promptAssembler, llmClientFactory, new KnowledgeProperties(), retrieveService,
            chatSessionService, aclService, metricsRecorder, new ObjectMapper(), Runnable::run, scheduler);
        QARequest request = new QARequest();
        request.setSpaceCode("missing");
        request.setQuery("question");

        service.streamAnswer(request, emitter);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(llmClientFactory, never()).getDefaultOrConfigured();
        verify(metricsRecorder).recordQaStatus("missing", "success");
    }

    @Test
    void revokedAclAfterRetrievalCannotLeakStreamingCitations() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties();
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceCode("space");
        when(spaceMapper.selectBySpaceCode("space")).thenReturn(space);
        ChunkHitVO hit = new ChunkHitVO();
        hit.setChunkId(1L);
        hit.setContent("secret citation");
        when(retrieveService.retrieveChunks(any())).thenReturn(List.of(hit));
        doNothing().doThrow(new AccessDeniedException("revoked"))
            .when(aclService).assertReadAccess("space");
        doReturn(heartbeat).when(scheduler)
            .scheduleAtFixedRate(any(Runnable.class), eq(15L), eq(15L), eq(TimeUnit.SECONDS));
        Executor directExecutor = Runnable::run;
        KnowledgeStreamQAServiceImpl service = new KnowledgeStreamQAServiceImpl(
            spaceMapper, promptAssembler, llmClientFactory, properties, retrieveService,
            chatSessionService, aclService, metricsRecorder, new ObjectMapper(), directExecutor, scheduler);
        QARequest request = new QARequest();
        request.setSpaceCode("space");
        request.setQuery("question");

        service.streamAnswer(request, emitter);

        verify(aclService, times(2)).assertReadAccess("space");
        verify(llmClientFactory, never()).get(any());
        verify(chatSessionService, never()).saveMessage(any(), any(), any(), any());
        // 仅允许 error 事件；citations 事件位于二次 ACL 校验之后，未被发送。
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(heartbeat).cancel(false);
    }
}
