package com.kma.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.ContentSecurityService;
import com.kma.knowledge.client.llm.LlmChatRequest;
import com.kma.knowledge.client.llm.LlmClient;
import com.kma.knowledge.client.llm.LlmClientFactory;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import com.kma.knowledge.rag.prompt.PromptAssembler;
import com.kma.knowledge.service.KnowledgeChatSessionService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import com.kma.knowledge.service.KnowledgeStreamQAService;
import com.kma.knowledge.service.CitationSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式问答服务实现
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeStreamQAServiceImpl implements KnowledgeStreamQAService {

    private final KnowledgeSpaceMapper spaceMapper;
    private final PromptAssembler promptAssembler;
    private final LlmClientFactory llmClientFactory;
    private final KnowledgeProperties properties;
    private final KnowledgeRetrieveServiceImpl retrieveService;
    private final KnowledgeChatSessionService chatSessionService;
    private final KnowledgeSpaceAclService aclService;
    private final RagMetricsRecorder metricsRecorder;
    private final ObjectMapper objectMapper;
    private final Executor kmaSseExecutor;
    private final ScheduledExecutorService kmaSseHeartbeatScheduler;
    private final ContentSecurityService contentSecurity;
    private final CitationSecurityService citationSecurity;

    @Autowired
    public KnowledgeStreamQAServiceImpl(KnowledgeSpaceMapper spaceMapper, PromptAssembler promptAssembler,
        LlmClientFactory llmClientFactory, KnowledgeProperties properties, KnowledgeRetrieveServiceImpl retrieveService,
        KnowledgeChatSessionService chatSessionService, KnowledgeSpaceAclService aclService,
        RagMetricsRecorder metricsRecorder, ObjectMapper objectMapper, Executor kmaSseExecutor,
        ScheduledExecutorService kmaSseHeartbeatScheduler, ContentSecurityService contentSecurity,
        CitationSecurityService citationSecurity) {
        this.spaceMapper=spaceMapper;this.promptAssembler=promptAssembler;this.llmClientFactory=llmClientFactory;
        this.properties=properties;this.retrieveService=retrieveService;this.chatSessionService=chatSessionService;
        this.aclService=aclService;this.metricsRecorder=metricsRecorder;this.objectMapper=objectMapper;
        this.kmaSseExecutor=kmaSseExecutor;this.kmaSseHeartbeatScheduler=kmaSseHeartbeatScheduler;
        this.contentSecurity=contentSecurity;this.citationSecurity=citationSecurity;
    }

    public KnowledgeStreamQAServiceImpl(KnowledgeSpaceMapper spaceMapper, PromptAssembler promptAssembler,
        LlmClientFactory llmClientFactory, KnowledgeProperties properties, KnowledgeRetrieveServiceImpl retrieveService,
        KnowledgeChatSessionService chatSessionService, KnowledgeSpaceAclService aclService,
        RagMetricsRecorder metricsRecorder, ObjectMapper objectMapper, Executor kmaSseExecutor,
        ScheduledExecutorService kmaSseHeartbeatScheduler) {
        this(spaceMapper,promptAssembler,llmClientFactory,properties,retrieveService,chatSessionService,aclService,
            metricsRecorder,objectMapper,kmaSseExecutor,kmaSseHeartbeatScheduler,null,null);
    }

    @Override
    public void streamAnswer(QARequest request, SseEmitter emitter) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(error -> cancelled.set(true));
        ScheduledFuture<?> heartbeat = kmaSseHeartbeatScheduler.scheduleAtFixedRate(() -> {
            if (cancelled.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ok"));
            } catch (IOException ex) {
                cancelled.set(true);
            }
        }, 15, 15, TimeUnit.SECONDS);

        kmaSseExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            String spaceCode = request.getSpaceCode();
            String status = "success";
            String errorMessage = null;
            try {
                ContentSecurityService.Inspection inputInspection = contentSecurity == null
                    ? new ContentSecurityService.Inspection(request.getQuery(), List.of(), false)
                    : contentSecurity.inspectUserInput(request.getQuery(), "space:" + spaceCode);
                aclService.assertReadAccess(spaceCode);
                KnowledgeSpace space = spaceMapper.selectBySpaceCode(spaceCode);
                boolean portalAll = Boolean.TRUE.equals(request.getPortalOnly()) && "*".equals(spaceCode);
                if (space == null && !portalAll) {
                    emitter.send(SseEmitter.event().name("error").data("空间不存在"));
                    emitter.complete();
                    return;
                }

                // 检索
                RetrieveRequest retrieveRequest = new RetrieveRequest();
                retrieveRequest.setQuery(request.getQuery());
                retrieveRequest.setSpaceCode(request.getSpaceCode());
                retrieveRequest.setSourceTags(request.getSourceTags());
                retrieveRequest.setTopK(request.getTopK());
                retrieveRequest.setPortalOnly(request.getPortalOnly());
                retrieveRequest.setContentTypes(request.getContentTypes());
                retrieveRequest.setTopicCodes(request.getTopicCodes());
                retrieveRequest.setValidityStatuses(request.getValidityStatuses());
                retrieveRequest.setDocId(request.getDocId());
                List<ChunkHitVO> hits = retrieveService.retrieveChunks(retrieveRequest);

                // 在引用和 Prompt 对外发送前二次复核，覆盖检索期间 ACL 被撤销的竞态窗口。
                aclService.assertReadAccess(spaceCode);
                if (citationSecurity != null) citationSecurity.verifyAndSanitize(spaceCode, hits,
                    Boolean.TRUE.equals(request.getPortalOnly()));

                // 会话
                Long sessionId = chatSessionService.createOrGetSession(
                    KmaIdentityContext.getUserId(), request.getSpaceCode(), inputInspection.sanitized(), request.getSessionId());
                List<String> history = chatSessionService.getRecentHistory(sessionId, 6);

                String prompt = promptAssembler.buildPromptWithHistory(inputInspection.sanitized(), hits, history);

                // 发送引用元数据事件
                emitter.send(SseEmitter.event().name("citations").data(objectMapper.writeValueAsString(hits)));

                // 调用统一流式 LLM；主模型失败时由工厂客户端按配置自动降级。
                KnowledgeProperties.LlmProperties llmProps = properties.getLlm();
                LlmClient llmClient = llmClientFactory.getDefaultOrConfigured();
                String provider = llmClient.provider();
                LlmChatRequest chatRequest = new LlmChatRequest();
                // 模型由具体 provider 选择，保证流式主备降级使用各自合法模型名。
                chatRequest.setModel(null);
                chatRequest.setStream(true);
                chatRequest.setTemperature(0.3);
                Map<String, String> systemMsg = new HashMap<>(2);
                systemMsg.put("role", "system");
                systemMsg.put("content", promptAssembler.getSystemPrompt());
                Map<String, String> userMsg = new HashMap<>(2);
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                chatRequest.setMessages(List.of(systemMsg, userMsg));

                StringBuilder answerBuilder = new StringBuilder();
                long llmStart = System.currentTimeMillis();
                llmClient.streamChat(chatRequest, chunk -> {
                    if (cancelled.get()) {
                        throw new CancellationException("SSE client disconnected");
                    }
                    String safeChunk = contentSecurity == null ? chunk
                        : contentSecurity.processModelOutput(chunk, "space:" + spaceCode).sanitized();
                    answerBuilder.append(safeChunk);
                    try {
                        emitter.send(SseEmitter.event().name("message").data(safeChunk));
                    } catch (IOException e) {
                        cancelled.set(true);
                        throw new CancellationException("SSE send failed: " + e.getMessage());
                    }
                });
                String metricModel = "ollama".equalsIgnoreCase(llmProps.getDefaultProvider())
                    ? llmProps.getLocal().getModel() : llmProps.getModel();
                metricsRecorder.recordLlmLatency(provider, metricModel,
                    System.currentTimeMillis() - llmStart);

                // 保存会话消息
                try {
                    chatSessionService.saveMessage(sessionId, "user", inputInspection.sanitized(), null);
                    chatSessionService.saveMessage(sessionId, "assistant", answerBuilder.toString(),
                        objectMapper.writeValueAsString(hits));
                } catch (Exception e) {
                    log.warn("保存流式会话消息失败", e);
                }

                emitter.send(SseEmitter.event().name("done").data(sessionId.toString()));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式问答异常", e);
                status = "failed";
                errorMessage = e.getMessage();
                try {
                    emitter.send(SseEmitter.event().name("error").data("服务异常：" + e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            } finally {
                cancelled.set(true);
                heartbeat.cancel(false);
                long costMillis = System.currentTimeMillis() - start;
                try {
                    metricsRecorder.recordQaLatency(spaceCode, costMillis);
                    metricsRecorder.recordQaStatus(spaceCode, status);
                } catch (Exception ex) {
                    log.warn("流式问答指标埋点失败", ex);
                }
            }
        });
    }
}



