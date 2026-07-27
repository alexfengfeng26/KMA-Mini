package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.dto.DocIngestTextRequest;
import com.kma.knowledge.dto.FeedTaskQueryRequest;
import com.kma.knowledge.dto.FeedTaskVO;
import com.kma.knowledge.entity.KnowledgeFeedTask;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.enums.FeedTaskStatus;
import com.kma.knowledge.mapper.KnowledgeFeedTaskMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.service.KnowledgeFeedTaskService;
import com.kma.knowledge.service.KnowledgeIngestionService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 知识库自动投喂任务服务实现
 *
 * @author party
 * @date 2026/07/02
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeFeedTaskServiceImpl implements KnowledgeFeedTaskService {

    private final KnowledgeFeedTaskMapper taskMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;
    private final KnowledgeSpaceAclService aclService;

    public KnowledgeFeedTaskServiceImpl(KnowledgeFeedTaskMapper taskMapper,
                                        KnowledgeSpaceMapper spaceMapper,
                                        KnowledgeIngestionService ingestionService,
                                        KnowledgeProperties properties,
                                        ObjectMapper objectMapper) {
        this(taskMapper, spaceMapper, ingestionService, properties, objectMapper, null);
    }

    @Autowired
    public KnowledgeFeedTaskServiceImpl(KnowledgeFeedTaskMapper taskMapper,
                                        KnowledgeSpaceMapper spaceMapper,
                                        KnowledgeIngestionService ingestionService,
                                        KnowledgeProperties properties,
                                        ObjectMapper objectMapper,
                                        KnowledgeSpaceAclService aclService) {
        this.taskMapper=taskMapper; this.spaceMapper=spaceMapper;
        this.ingestionService=ingestionService; this.properties=properties; this.objectMapper=objectMapper;
        this.aclService=aclService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public Long submit(String sourceType, Long sourceId, Long sourceVersionId, String spaceCode, DocIngestTextRequest request) {
        if (!StringUtils.hasText(sourceType) || sourceId == null || !StringUtils.hasText(spaceCode) || request == null) {
            log.warn("投喂任务参数不完整, sourceType={}, sourceId={}, spaceCode={}", sourceType, sourceId, spaceCode);
            return null;
        }
        if (!StringUtils.hasText(request.getTitle()) || !StringUtils.hasText(request.getContent())) {
            log.warn("投喂任务内容不完整, sourceType={}, sourceId={}", sourceType, sourceId);
            return null;
        }

        KnowledgeSpace space = spaceMapper.selectBySpaceCode(spaceCode);
        if (space == null) {
            log.warn("投喂目标空间不存在, spaceCode={}, sourceType={}, sourceId={}", spaceCode, sourceType, sourceId);
            return null;
        }
        if (aclService != null && KmaIdentityContext.getLoginUser() != null) aclService.assertIngestAccess(spaceCode);

        KnowledgeFeedTask exist = taskMapper.selectOne(new LambdaQueryWrapper<KnowledgeFeedTask>()
                .eq(KnowledgeFeedTask::getSourceType, sourceType)
                .eq(KnowledgeFeedTask::getSourceId, sourceId)
                .eq(KnowledgeFeedTask::getSourceVersionId, sourceVersionId)
                .eq(KnowledgeFeedTask::getSpaceCode, spaceCode)
                .in(KnowledgeFeedTask::getStatus, FeedTaskStatus.PENDING.getCode(), FeedTaskStatus.PROCESSING.getCode()));
        if (exist != null) {
            log.debug("投喂任务已存在, taskId={}, sourceType={}, sourceId={}", exist.getTaskId(), sourceType, sourceId);
            return exist.getTaskId();
        }

        KnowledgeFeedTask task = new KnowledgeFeedTask();
        task.setSourceType(sourceType);
        task.setSourceId(sourceId);
        task.setSourceVersionId(sourceVersionId);
        task.setSpaceCode(spaceCode);
        task.setStatus(FeedTaskStatus.PENDING.getCode());
        task.setRetryCount(0);
        task.setMaxRetry(properties.getFeed().getMaxRetry());
        task.setNextExecuteTime(LocalDateTime.now());
        task.setErrorMessage(null);
        task.setMeta(serializePayload(request));
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);

        log.info("投喂任务已创建, taskId={}, sourceType={}, sourceId={}, spaceCode={}",
                task.getTaskId(), sourceType, sourceId, spaceCode);
        return task.getTaskId();
    }

    @Override
    public Page<FeedTaskVO> page(FeedTaskQueryRequest request) {
        Page<KnowledgeFeedTask> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<KnowledgeFeedTask> wrapper = new LambdaQueryWrapper<>();
        Set<String> readableSpaceCodes = readableSpaceCodes();
        if (readableSpaceCodes != null && readableSpaceCodes.isEmpty()) wrapper.eq(KnowledgeFeedTask::getSpaceCode, "__none__");
        else if (readableSpaceCodes != null) wrapper.in(KnowledgeFeedTask::getSpaceCode, readableSpaceCodes);
        if (StringUtils.hasText(request.getSourceType())) {
            wrapper.eq(KnowledgeFeedTask::getSourceType, request.getSourceType());
        }
        if (StringUtils.hasText(request.getSpaceCode())) {
            wrapper.eq(KnowledgeFeedTask::getSpaceCode, request.getSpaceCode());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(KnowledgeFeedTask::getStatus, request.getStatus());
        }
        wrapper.orderByDesc(KnowledgeFeedTask::getCreateTime);

        Page<KnowledgeFeedTask> ApiResult = taskMapper.selectPage(page, wrapper);
        List<FeedTaskVO> records = ApiResult.getRecords().stream().map(this::toVO).toList();
        Page<FeedTaskVO> voPage = new Page<>(ApiResult.getCurrent(), ApiResult.getSize(), ApiResult.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public Map<String, Long> stats() {
        Map<String, Long> result = new LinkedHashMap<>();
        QueryWrapper<KnowledgeFeedTask> query = new QueryWrapper<KnowledgeFeedTask>()
            .select("status", "COUNT(*) AS count");
        Set<String> readableSpaceCodes = readableSpaceCodes();
        if (readableSpaceCodes != null && readableSpaceCodes.isEmpty()) query.eq("space_code", "__none__");
        else if (readableSpaceCodes != null) query.in("space_code", readableSpaceCodes);
        query.groupBy("status");
        for (Map<String, Object> row : taskMapper.selectMaps(query)) {
            result.put(String.valueOf(row.get("status")), ((Number) row.get("count")).longValue());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void retry(Long taskId) {
        KnowledgeFeedTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new KmaException("投喂任务不存在");
        }
        if (aclService != null) aclService.assertIngestAccess(task.getSpaceCode());
        task.setStatus(FeedTaskStatus.PENDING.getCode());
        task.setRetryCount(0);
        task.setNextExecuteTime(LocalDateTime.now());
        task.setErrorMessage(null);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("投喂任务已手动重置, taskId={}", taskId);

        // 立即尝试执行一次
        executeTask(task);
    }

    @Override
    @Scheduled(fixedDelayString = "${knowledge.feed.fixed-delay:30000}")
    public void processPendingTasks() {
        if (!properties.getFeed().isEnabled()) {
            return;
        }
        processPendingBatch();
    }

    private void processPendingBatch() {
        int batchSize = properties.getFeed().getBatchSize();
        LambdaQueryWrapper<KnowledgeFeedTask> wrapper = new LambdaQueryWrapper<KnowledgeFeedTask>()
                .in(KnowledgeFeedTask::getStatus, FeedTaskStatus.PENDING.getCode(), FeedTaskStatus.PROCESSING.getCode())
                .le(KnowledgeFeedTask::getNextExecuteTime, LocalDateTime.now())
                .lt(KnowledgeFeedTask::getRetryCount, properties.getFeed().getMaxRetry())
                .orderByAsc(KnowledgeFeedTask::getNextExecuteTime)
                .last("LIMIT " + batchSize);

        List<KnowledgeFeedTask> tasks = taskMapper.selectList(wrapper);
        if (tasks.isEmpty()) {
            return;
        }
        log.debug("本轮调度待处理投喂任务数: count={}", tasks.size());
        for (KnowledgeFeedTask task : tasks) {
            try {
                executeTask(task);
            } catch (Exception e) {
                log.error("投喂任务执行异常, taskId={}", task.getTaskId(), e);
            }
        }
    }

    private void executeTask(KnowledgeFeedTask task) {
        // 乐观锁：只处理当前为 pending，或 processing 但已超时的任务
        LambdaUpdateWrapper<KnowledgeFeedTask> lockWrapper = new LambdaUpdateWrapper<KnowledgeFeedTask>()
                .eq(KnowledgeFeedTask::getTaskId, task.getTaskId())
                .and(w -> w.eq(KnowledgeFeedTask::getStatus, FeedTaskStatus.PENDING.getCode())
                        .or(o -> o.eq(KnowledgeFeedTask::getStatus, FeedTaskStatus.PROCESSING.getCode())
                                .le(KnowledgeFeedTask::getNextExecuteTime, LocalDateTime.now())));
        KnowledgeFeedTask processing = new KnowledgeFeedTask();
        processing.setTaskId(task.getTaskId());
        processing.setStatus(FeedTaskStatus.PROCESSING.getCode());
        processing.setUpdateTime(LocalDateTime.now());
        int rows = taskMapper.update(processing, lockWrapper);
        if (rows == 0) {
            log.debug("投喂任务已被其他节点处理, taskId={}", task.getTaskId());
            return;
        }

        FeedPayload payload;
        try {
            payload = objectMapper.readValue(task.getMeta(), FeedPayload.class);
        } catch (Exception e) {
            markDead(task, "任务元数据解析失败: " + e.getMessage());
            return;
        }

        DocIngestTextRequest request = new DocIngestTextRequest();
        request.setSpaceCode(task.getSpaceCode());
        request.setTitle(payload.getTitle());
        request.setContent(payload.getContent());
        request.setSourceTag(payload.getSourceTag());
        request.setExternalRef(payload.getExternalRef());
        request.setSourceVersion(task.getSourceVersionId());
        request.setMeta(payload.getMeta());

        try {
            ingestionService.ingestTextAsSystem(request);
            markSuccess(task);
        } catch (Exception e) {
            handleFailure(task, e);
        }
    }

    private void markSuccess(KnowledgeFeedTask task) {
        KnowledgeFeedTask update = new KnowledgeFeedTask();
        update.setTaskId(task.getTaskId());
        update.setStatus(FeedTaskStatus.SUCCESS.getCode());
        update.setErrorMessage(null);
        update.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(update);
        log.info("投喂任务执行成功, taskId={}", task.getTaskId());
    }

    private void handleFailure(KnowledgeFeedTask task, Exception e) {
        String message = truncate(e.getMessage(), 500);
        int nextRetry = task.getRetryCount() + 1;
        if (nextRetry >= task.getMaxRetry()) {
            markDead(task, message);
            return;
        }

        long backoffSeconds = Math.min(
                properties.getFeed().getInitialIntervalSeconds() * (1L << nextRetry),
                properties.getFeed().getMaxIntervalSeconds());
        LocalDateTime nextTime = LocalDateTime.now().plusSeconds(backoffSeconds);

        KnowledgeFeedTask update = new KnowledgeFeedTask();
        update.setTaskId(task.getTaskId());
        update.setStatus(FeedTaskStatus.PENDING.getCode());
        update.setRetryCount(nextRetry);
        update.setNextExecuteTime(nextTime);
        update.setErrorMessage(message);
        update.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(update);

        log.warn("投喂任务失败, 将按指数退避重试, taskId={}, retryCount={}, nextTime={}, error={}",
                task.getTaskId(), nextRetry, nextTime, message);
    }

    private void markDead(KnowledgeFeedTask task, String message) {
        KnowledgeFeedTask update = new KnowledgeFeedTask();
        update.setTaskId(task.getTaskId());
        update.setStatus(FeedTaskStatus.DEAD.getCode());
        update.setErrorMessage(message);
        update.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(update);
        log.error("投喂任务进入死信, taskId={}, error={}", task.getTaskId(), message);
    }

    private String serializePayload(DocIngestTextRequest request) {
        FeedPayload payload = new FeedPayload();
        payload.setTitle(request.getTitle());
        payload.setContent(request.getContent());
        payload.setSourceTag(request.getSourceTag());
        payload.setExternalRef(request.getExternalRef());
        payload.setMeta(request.getMeta());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new KmaException("投喂任务元数据序列化失败", e);
        }
    }

    private FeedTaskVO toVO(KnowledgeFeedTask task) {
        FeedTaskVO vo = new FeedTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setSourceType(task.getSourceType());
        vo.setSourceId(task.getSourceId());
        vo.setSourceVersionId(task.getSourceVersionId());
        vo.setSpaceCode(task.getSpaceCode());
        vo.setStatus(task.getStatus());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setNextExecuteTime(task.getNextExecuteTime());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setMeta(task.getMeta());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }

    private Set<String> readableSpaceCodes() {
        if (aclService == null) return null;
        Set<Long> ids = aclService.getReadableSpaceIds();
        if (ids == null) return null;
        if (ids.isEmpty()) return Collections.emptySet();
        return spaceMapper.selectByIds(ids).stream().map(KnowledgeSpace::getSpaceCode)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 投喂任务体内保存的摄入请求快照
     */
    private static class FeedPayload {
        private String title;
        private String content;
        private String sourceTag;
        private String externalRef;
        private String meta;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSourceTag() {
            return sourceTag;
        }

        public void setSourceTag(String sourceTag) {
            this.sourceTag = sourceTag;
        }

        public String getExternalRef() {
            return externalRef;
        }

        public void setExternalRef(String externalRef) {
            this.externalRef = externalRef;
        }

        public String getMeta() {
            return meta;
        }

        public void setMeta(String meta) {
            this.meta = meta;
        }
    }
}



