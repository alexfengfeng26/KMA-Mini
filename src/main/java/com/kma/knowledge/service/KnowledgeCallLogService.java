package com.kma.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.knowledge.dto.CallLogQueryRequest;
import com.kma.knowledge.dto.KnowledgeCallLogVO;
import com.kma.knowledge.entity.KnowledgeCallLog;

/**
 * RAG 调用日志服务接口
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeCallLogService {

    /**
     * 记录调用日志
     */
    void save(KnowledgeCallLog log);

    /**
     * 分页查询调用日志
     */
    Page<KnowledgeCallLogVO> page(CallLogQueryRequest request);

    /**
     * 查询调用日志详情
     */
    KnowledgeCallLogVO getById(Long logId);
}



