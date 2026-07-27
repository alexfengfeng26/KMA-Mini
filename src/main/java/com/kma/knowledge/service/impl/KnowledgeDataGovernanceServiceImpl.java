package com.kma.knowledge.service.impl;

import com.kma.common.exception.KmaException;
import com.kma.knowledge.config.KnowledgeProperties;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.service.KnowledgeDataGovernanceService;
import com.kma.knowledge.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库数据治理服务实现
 *
 * @author party
 * @date 2026/07/02
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeDataGovernanceServiceImpl implements KnowledgeDataGovernanceService {

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeProperties properties;

    @Override
    @Scheduled(cron = "${knowledge.governance.cron:0 0 2 * * ?}")
    public void cleanup() {
        if (!properties.getGovernance().isEnabled()) {
            return;
        }
        log.info("开始知识库数据治理清理");

        int orphanChunks = chunkMapper.deleteOrphanChunks();
        int failedDocs = docMapper.deleteFailedDocsOlderThan(properties.getGovernance().getFailedDocRetentionDays());

        log.info("知识库数据治理清理完成, orphanChunks={}, failedDocs={}",
            orphanChunks, failedDocs);
    }

    @Override
    public void reindexSpace(String spaceCode) {
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(spaceCode);
        if (space == null) {
            throw new KmaException("知识空间不存在: " + spaceCode);
        }
        List<KnowledgeDoc> docs = docMapper.selectBySpaceId(space.getSpaceId());
        if (docs.isEmpty()) {
            log.info("空间无文档需要重新索引, spaceCode={}", spaceCode);
            return;
        }
        log.info("开始重新索引全空间, spaceCode={}, docCount={}", spaceCode, docs.size());
        for (KnowledgeDoc doc : docs) {
            try {
                ingestionService.reindex(doc.getDocId());
            } catch (Exception e) {
                log.warn("重新索引文档失败, docId={}, spaceCode={}", doc.getDocId(), spaceCode, e);
            }
        }
        log.info("重新索引全空间完成, spaceCode={}", spaceCode);
    }
}



