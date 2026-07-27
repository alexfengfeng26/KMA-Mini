package com.kma.knowledge.service.impl;

import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.RetrieveDebugResult;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.dto.RetrieveResult;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.rag.retrieve.HybridRetriever;
import com.kma.knowledge.service.KnowledgeRetrieveService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;

/**
 * 知识库检索服务实现
 *
 * @author party
 * @date 2026/06/30
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeRetrieveServiceImpl implements KnowledgeRetrieveService {

    private final HybridRetriever hybridRetriever;
    private final KnowledgeSpaceAclService aclService;

    @Override
    public RetrieveResult retrieve(RetrieveRequest request) {
        aclService.assertReadAccess(request.getSpaceCode());
        List<ChunkHitVO> hits = hybridRetriever.retrieve(request);
        RetrieveResult ApiResult = new RetrieveResult();
        ApiResult.setQuery(request.getQuery());
        ApiResult.setSpaceCode(request.getSpaceCode());
        ApiResult.setTopK(request.getTopK());
        ApiResult.setHits(hits);
        return ApiResult;
    }

    @Override
    public RetrieveDebugResult debug(RetrieveRequest request) {
        aclService.assertReadAccess(request.getSpaceCode());
        HybridRetriever.RetrieveDebugContext ctx = hybridRetriever.retrieveDebug(request);
        RetrieveDebugResult ApiResult = new RetrieveDebugResult();
        ApiResult.setQuery(request.getQuery());
        ApiResult.setSpaceCode(request.getSpaceCode());
        ApiResult.setVectorHits(ctx.getVectorHits());
        ApiResult.setFullTextHits(ctx.getFullTextHits());
        ApiResult.setRerankedHits(ctx.getRerankedHits());
        ApiResult.setFinalHits(ctx.getFinalHits());
        ApiResult.setLatency(ctx.getLatency());
        return ApiResult;
    }

    @Override
    public List<ChunkHitVO> retrieveChunks(RetrieveRequest request) {
        if (Boolean.TRUE.equals(request.getPortalOnly()) && "*".equals(request.getSpaceCode())) {
            List<KnowledgeSpace> spaces = aclService.getReadableSpaces();
            if (spaces.isEmpty()) {
                aclService.assertReadAccess(request.getSpaceCode());
            }
            int topK = request.getTopK() == null ? 5 : request.getTopK();
            List<ChunkHitVO> merged = new ArrayList<>();
            for (KnowledgeSpace space : spaces) {
                RetrieveRequest scoped = copyForSpace(request, space.getSpaceCode());
                merged.addAll(hybridRetriever.retrieve(scoped));
            }
            return merged.stream()
                .sorted(Comparator.comparing(ChunkHitVO::getScore,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .toList();
        }
        aclService.assertReadAccess(request.getSpaceCode());
        return hybridRetriever.retrieve(request);
    }

    private RetrieveRequest copyForSpace(RetrieveRequest source, String spaceCode) {
        RetrieveRequest target = new RetrieveRequest();
        target.setQuery(source.getQuery());
        target.setSpaceCode(spaceCode);
        target.setSourceTags(source.getSourceTags());
        target.setTopK(source.getTopK());
        target.setScoreThreshold(source.getScoreThreshold());
        target.setPortalOnly(source.getPortalOnly());
        target.setContentTypes(source.getContentTypes());
        target.setTopicCodes(source.getTopicCodes());
        target.setValidityStatuses(source.getValidityStatuses());
        target.setDocId(source.getDocId());
        return target;
    }
}



