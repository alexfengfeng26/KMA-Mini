package com.kma.knowledge.service;

import com.kma.knowledge.dto.QARequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式问答服务接口
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeStreamQAService {

    /**
     * 流式问答（SSE）
     */
    void streamAnswer(QARequest request, SseEmitter emitter);
}



