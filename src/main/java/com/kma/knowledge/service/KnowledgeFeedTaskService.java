package com.kma.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.knowledge.dto.DocIngestTextRequest;
import com.kma.knowledge.dto.FeedTaskQueryRequest;
import com.kma.knowledge.dto.FeedTaskVO;

import java.util.Map;

/**
 * 知识库自动投喂任务服务
 *
 * <p>
 * 负责把接入系统的文档变更事件沉淀为本地任务表，
 * 并按指数退避重试，最终失败进入死信队列。
 *
 * @author party
 * @date 2026/07/02
 */
public interface KnowledgeFeedTaskService {

    /**
     * 提交一条投喂任务。
     * <p>
     * 若同一业务来源已存在待处理或处理中任务，则返回已有任务 ID，避免重复。
     *
     * @param sourceType       接入方自定义来源类型
     * @param sourceId         业务对象 ID
     * @param sourceVersionId  业务对象版本 ID（可选）
     * @param spaceCode        目标空间编码
     * @param request          摄入请求（含标题、内容、标签、外部引用、元数据）
     * @return 任务 ID，若空间不存在返回 null
     */
    Long submit(String sourceType, Long sourceId, Long sourceVersionId, String spaceCode, DocIngestTextRequest request);

    /**
     * 分页查询投喂任务
     */
    Page<FeedTaskVO> page(FeedTaskQueryRequest request);

    /** 按状态汇总全局任务积压。 */
    Map<String, Long> stats();

    /**
     * 手动重试指定任务：重置为待处理状态并立即执行
     */
    void retry(Long taskId);

    /**
     * 处理到达执行时间的待处理任务（由调度器调用）
     */
    void processPendingTasks();
}



