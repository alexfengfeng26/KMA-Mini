package com.kma.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.kma.common.result.ApiResult;
import com.kma.common.security.KmaIdentityContext;
import com.kma.knowledge.entity.KnowledgeChatMessage;
import com.kma.knowledge.entity.KnowledgeChatSession;
import com.kma.knowledge.service.KnowledgeChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 问答会话接口
 *
 * @author party
 * @date 2026/06/30
 */
@Tag(name = "KnowledgeChatSession", description = "KnowledgeChatSession 接口")
@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeChatSessionController {

    private final KnowledgeChatSessionService chatSessionService;

    @Operation(summary = "list")
    @GetMapping
    @PreAuthorize("@ss.hasPermi('chat:read')")
    public ApiResult<List<KnowledgeChatSession>> list(@RequestParam(required = false) String spaceCode) {
        return ApiResult.success(chatSessionService.listUserSessions(KmaIdentityContext.getUserId(), spaceCode));
    }

    @GetMapping("/{sessionId}/messages")
    @PreAuthorize("@ss.hasPermi('chat:read')")
    public ApiResult<List<KnowledgeChatMessage>> messages(@PathVariable Long sessionId) {
        return ApiResult.success(chatSessionService.listMessages(KmaIdentityContext.getUserId(), sessionId));
    }
}



