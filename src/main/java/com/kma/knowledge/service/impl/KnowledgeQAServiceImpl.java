package com.kma.knowledge.service.impl;

import com.kma.common.exception.KmaException;
import com.kma.common.result.KmaResultCode;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.ContentSecurityService;
import com.kma.knowledge.client.llm.LlmChatRequest;
import com.kma.knowledge.client.llm.LlmChatResponse;
import com.kma.knowledge.client.llm.LlmClient;
import com.kma.knowledge.client.llm.LlmClientFactory;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.QARequest;
import com.kma.knowledge.dto.QAResult;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.entity.KnowledgeCallLog;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.enums.RagMode;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.metrics.RagMetricsRecorder;
import com.kma.knowledge.rag.prompt.PromptAssembler;
import com.kma.knowledge.service.KnowledgeCallLogService;
import com.kma.knowledge.service.KnowledgeChatSessionService;
import com.kma.knowledge.service.KnowledgeQAService;
import com.kma.knowledge.service.KnowledgeRetrieveService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import com.kma.knowledge.service.CitationSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库问答服务实现
 *
 * @author party
 * @date 2026/06/30
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeQAServiceImpl implements KnowledgeQAService {

    private final KnowledgeRetrieveService retrieveService;
    private final PromptAssembler promptAssembler;
    private final LlmClientFactory llmClientFactory;
    private final KnowledgeProperties properties;
    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeCallLogService callLogService;
    private final KnowledgeChatSessionService chatSessionService;
    private final KnowledgeSpaceAclService aclService;
    private final RagMetricsRecorder metricsRecorder;
    private final ObjectMapper objectMapper;
    private final ContentSecurityService contentSecurity;
    private final CitationSecurityService citationSecurity;

    @Autowired
    public KnowledgeQAServiceImpl(KnowledgeRetrieveService retrieveService, PromptAssembler promptAssembler,
                                  LlmClientFactory llmClientFactory, KnowledgeProperties properties,
                                  KnowledgeSpaceMapper spaceMapper, KnowledgeCallLogService callLogService,
                                  KnowledgeChatSessionService chatSessionService, KnowledgeSpaceAclService aclService,
                                  RagMetricsRecorder metricsRecorder, ObjectMapper objectMapper,
                                  ContentSecurityService contentSecurity, CitationSecurityService citationSecurity) {
        this.retrieveService=retrieveService; this.promptAssembler=promptAssembler; this.llmClientFactory=llmClientFactory;
        this.properties=properties; this.spaceMapper=spaceMapper; this.callLogService=callLogService;
        this.chatSessionService=chatSessionService; this.aclService=aclService; this.metricsRecorder=metricsRecorder;
        this.objectMapper=objectMapper; this.contentSecurity=contentSecurity; this.citationSecurity=citationSecurity;
    }

    public KnowledgeQAServiceImpl(KnowledgeRetrieveService retrieveService, PromptAssembler promptAssembler,
                                  LlmClientFactory llmClientFactory, KnowledgeProperties properties,
                                  KnowledgeSpaceMapper spaceMapper, KnowledgeCallLogService callLogService,
                                  KnowledgeChatSessionService chatSessionService, KnowledgeSpaceAclService aclService,
                                  RagMetricsRecorder metricsRecorder, ObjectMapper objectMapper) {
        this(retrieveService,promptAssembler,llmClientFactory,properties,spaceMapper,callLogService,
            chatSessionService,aclService,metricsRecorder,objectMapper,null,null);
    }

    @Override
    public QAResult answer(QARequest request) {
        long start = System.currentTimeMillis();
        ContentSecurityService.Inspection inputInspection = contentSecurity == null
            ? new ContentSecurityService.Inspection(request.getQuery(), List.of(), false)
            : contentSecurity.inspectUserInput(request.getQuery(), "space:" + request.getSpaceCode());
        boolean portalAll = Boolean.TRUE.equals(request.getPortalOnly()) && "*".equals(request.getSpaceCode());
        aclService.assertReadAccess(request.getSpaceCode());
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(request.getSpaceCode());
        if (space == null && !portalAll) {
            throw new RuntimeException("知识空间不存在: " + request.getSpaceCode());
        }

        // 1. 检索
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

        // 检索结束后再次复核权限，防止检索期间 ACL 被撤销后仍泄漏引用或上下文。
        aclService.assertReadAccess(request.getSpaceCode());
        if (citationSecurity != null) citationSecurity.verifyAndSanitize(request.getSpaceCode(), hits,
            Boolean.TRUE.equals(request.getPortalOnly()));

        // 2. 会话上下文
        Long sessionId = chatSessionService.createOrGetSession(
            KmaIdentityContext.getUserId(), request.getSpaceCode(), inputInspection.sanitized(), request.getSessionId());
        List<String> history = chatSessionService.getRecentHistory(
            sessionId, properties.getRag().getMaxHistoryMessages());

        if (hits.isEmpty()) {
            QAResult result = new QAResult();
            result.setAnswer(properties.getRag().getNoEvidenceAnswer());
            result.setAnswered(false);
            result.setReason("NO_EVIDENCE");
            result.setCitations(List.of());
            result.setSessionId(sessionId);
            saveConversation(sessionId, inputInspection.sanitized(), result.getAnswer(), List.of());
            recordOutcome(request, hits, null, "no_evidence", null, start, inputInspection.flags());
            return result;
        }

        // 3. 组装 Prompt
        String prompt = promptAssembler.buildPromptWithHistory(inputInspection.sanitized(), hits, history);

        // 4. 调用 LLM（使用 knowledge.llm.* 默认配置，空间表暂不支持独立 LLM 提供商）
        KnowledgeProperties.LlmProperties llmProps = properties.getLlm();
        LlmClient llmClient = llmClientFactory.getDefaultOrConfigured();
        if (llmClient == null) {
            // Keeps older integrations/mocks compatible; the real factory never returns null.
            llmClient = llmClientFactory.get(llmProps.getDefaultProvider());
        }

        LlmChatRequest chatRequest = new LlmChatRequest();
        // 模型由具体 provider 从各自配置选择，确保主备降级时不会把模型名串到另一供应商。
        chatRequest.setModel(null);
        chatRequest.setStream(request.getStream() != null && request.getStream());
        chatRequest.setTemperature(0.3);

        Map<String, String> systemMsg = new HashMap<>(2);
        systemMsg.put("role", "system");
        systemMsg.put("content", promptAssembler.getSystemPrompt());
        Map<String, String> userMsg = new HashMap<>(2);
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        chatRequest.setMessages(List.of(systemMsg, userMsg));

        LlmChatResponse chatResponse;
        String status = "success";
        String errorMessage = null;
        long llmStart = System.currentTimeMillis();
        try {
            chatResponse = llmClient.chat(chatRequest);
        } catch (Exception e) {
            status = "failed";
            errorMessage = e.getMessage();
            log.error("问答调用失败", e);
            recordOutcome(request, hits, null, status, errorMessage, start, inputInspection.flags());
            throw new KmaException(KmaResultCode.SERVICE_UNAVAILABLE, "问答模型暂不可用，请稍后重试");
        }
        ContentSecurityService.Inspection outputInspection = contentSecurity == null
            ? new ContentSecurityService.Inspection(chatResponse.getContent(), List.of(), false)
            : contentSecurity.processModelOutput(chatResponse.getContent(), "space:" + request.getSpaceCode());
        chatResponse.setContent(outputInspection.sanitized());
        List<String> securityFlags = new java.util.ArrayList<>(inputInspection.flags());
        outputInspection.flags().stream().filter(flag -> !securityFlags.contains(flag)).forEach(securityFlags::add);
        String metricModel = chatResponse.getModel() != null ? chatResponse.getModel()
            : configuredModel(llmProps.getDefaultProvider(), llmProps);
        metricsRecorder.recordLlmLatency(llmProps.getDefaultProvider(), metricModel,
            System.currentTimeMillis() - llmStart);

        // 5. 构造结果并保存会话消息
        QAResult ApiResult = new QAResult();
        ApiResult.setAnswer(chatResponse.getContent());
        ApiResult.setAnswered(true);
        ApiResult.setCitations(hits);
        ApiResult.setPromptTokens(chatResponse.getPromptTokens());
        ApiResult.setCompletionTokens(chatResponse.getCompletionTokens());
        ApiResult.setLlmModel(chatResponse.getModel());
        ApiResult.setSessionId(sessionId);

        saveConversation(sessionId, inputInspection.sanitized(), chatResponse.getContent(), hits);

        recordOutcome(request, hits, chatResponse, status, errorMessage, start, securityFlags);
        return ApiResult;
    }

    private void saveConversation(Long sessionId, String query, String answer, List<ChunkHitVO> hits) {
        try {
            chatSessionService.saveMessage(sessionId, "user", query, null);
            String citationsJson = objectMapper.writeValueAsString(hits);
            chatSessionService.saveMessage(sessionId, "assistant", answer, citationsJson);
        } catch (Exception e) {
            log.warn("保存会话消息失败: sessionId={}", sessionId, e);
        }
    }

    private void recordOutcome(QARequest request, List<ChunkHitVO> hits, LlmChatResponse response,
                               String status, String errorMessage, long start, List<String> securityFlags) {
        try {
            long costMillis = System.currentTimeMillis() - start;
            metricsRecorder.recordQaLatency(request.getSpaceCode(), costMillis);
            String model = response == null ? null : response.getModel();
            Integer promptTokens = response == null ? null : response.getPromptTokens();
            Integer completionTokens = response == null ? null : response.getCompletionTokens();
            metricsRecorder.recordQaTokens(request.getSpaceCode(), model,
                promptTokens != null ? promptTokens : 0, completionTokens != null ? completionTokens : 0);
            metricsRecorder.recordQaStatus(request.getSpaceCode(), status);

            KnowledgeCallLog callLog = new KnowledgeCallLog();
            callLog.setUserId(KmaIdentityContext.getUserId());
            callLog.setUsername(KmaIdentityContext.getUsername());
            callLog.setSpaceCode(request.getSpaceCode());
            callLog.setRagMode(RagMode.QA.getCode());
            callLog.setQuery(contentSecurity == null ? request.getQuery() : contentSecurity.redactForAudit(request.getQuery()));
            callLog.setTopK(request.getTopK());
            callLog.setSourceTags(request.getSourceTags() != null
                ? String.join(",", request.getSourceTags()) : null);
            callLog.setHitCount(hits.size());
            callLog.setPromptTokens(promptTokens);
            callLog.setCompletionTokens(completionTokens);
            callLog.setCostMillis((int) costMillis);
            callLog.setLlmModel(model);
            callLog.setStatus(status);
            callLog.setErrorMessage(errorMessage);
            callLog.setSecurityFlags(objectMapper.writeValueAsString(securityFlags));
            callLog.setCreateTime(LocalDateTime.now());
            callLogService.save(callLog);
        } catch (Exception e) {
            log.warn("保存问答日志/指标失败", e);
        }
    }

    private int value(Integer value) { return value == null ? 0 : Math.max(0, value); }

    private String configuredModel(String provider, KnowledgeProperties.LlmProperties properties) {
        return "ollama".equalsIgnoreCase(provider)
            ? properties.getLocal().getModel() : properties.getModel();
    }
}
