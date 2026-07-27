package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.dto.CallLogQueryRequest;
import com.kma.knowledge.dto.KnowledgeCallLogVO;
import com.kma.knowledge.entity.KnowledgeCallLog;
import com.kma.knowledge.mapper.KnowledgeCallLogMapper;
import com.kma.knowledge.service.KnowledgeCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * RAG 调用日志服务实现
 *
 * @author party
 * @date 2026/06/30
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeCallLogServiceImpl implements KnowledgeCallLogService {

    private final KnowledgeCallLogMapper callLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void save(KnowledgeCallLog log) {
        callLogMapper.insert(log);
    }

    @Override
    public Page<KnowledgeCallLogVO> page(CallLogQueryRequest request) {
        Page<KnowledgeCallLog> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<KnowledgeCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getSpaceCode()), KnowledgeCallLog::getSpaceCode, request.getSpaceCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), KnowledgeCallLog::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getRagMode()), KnowledgeCallLog::getRagMode, request.getRagMode());
        wrapper.eq(request.getUserId() != null, KnowledgeCallLog::getUserId, request.getUserId());
        wrapper.like(StringUtils.hasText(request.getUsername()), KnowledgeCallLog::getUsername, request.getUsername());
        wrapper.ge(request.getStartTime() != null, KnowledgeCallLog::getCreateTime, request.getStartTime());
        wrapper.le(request.getEndTime() != null, KnowledgeCallLog::getCreateTime, request.getEndTime());
        wrapper.orderByDesc(KnowledgeCallLog::getCreateTime);

        Page<KnowledgeCallLog> ApiResult = callLogMapper.selectPage(page, wrapper);
        Page<KnowledgeCallLogVO> voPage = new Page<>(ApiResult.getCurrent(), ApiResult.getSize(), ApiResult.getTotal());
        voPage.setRecords(ApiResult.getRecords().stream()
            .map(this::toVO)
            .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public KnowledgeCallLogVO getById(Long logId) {
        KnowledgeCallLog entity = callLogMapper.selectById(logId);
        if (entity == null) {
            throw new KmaException("调用日志不存在");
        }
        return toVO(entity);
    }

    private KnowledgeCallLogVO toVO(KnowledgeCallLog entity) {
        KnowledgeCallLogVO vo = new KnowledgeCallLogVO();
        vo.setLogId(entity.getLogId());
        vo.setUserId(entity.getUserId());
        vo.setUsername(entity.getUsername());
        vo.setSpaceCode(entity.getSpaceCode());
        vo.setRagMode(entity.getRagMode());
        vo.setQuery(entity.getQuery());
        vo.setTopK(entity.getTopK());
        vo.setSourceTags(entity.getSourceTags());
        vo.setHitCount(entity.getHitCount());
        vo.setPromptTokens(entity.getPromptTokens());
        vo.setCompletionTokens(entity.getCompletionTokens());
        vo.setCostMillis(entity.getCostMillis());
        vo.setLlmModel(entity.getLlmModel());
        vo.setStatus(entity.getStatus());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setSecurityFlags(entity.getSecurityFlags());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}



