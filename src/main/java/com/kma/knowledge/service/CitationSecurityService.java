package com.kma.knowledge.service;

import com.kma.common.security.ContentSecurityService;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.entity.KnowledgeChunk;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CitationSecurityService {
    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeSpaceAclService aclService;
    private final ContentSecurityService contentSecurity;

    public void verifyAndSanitize(String spaceCode, List<ChunkHitVO> hits) {
        verifyAndSanitize(spaceCode, hits, false);
    }

    public void verifyAndSanitize(String spaceCode, List<ChunkHitVO> hits, boolean portalOnly) {
        if ("*".equals(spaceCode)) {
            if (hits == null || hits.isEmpty()) {
                aclService.assertReadAccess(spaceCode);
                return;
            }
            Map<String, List<ChunkHitVO>> bySpace = hits.stream().collect(Collectors.groupingBy(hit -> {
                if (hit.getSpaceCode() == null || hit.getSpaceCode().isBlank()) {
                    throw new AccessDeniedException("跨空间引用缺少空间标识");
                }
                return hit.getSpaceCode();
            }));
            bySpace.forEach((actualSpace, groupedHits) -> verifyAndSanitize(actualSpace, groupedHits, portalOnly));
            return;
        }
        aclService.assertReadAccess(spaceCode);
        if (hits == null || hits.isEmpty()) return;
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(spaceCode);
        if (space == null) throw new AccessDeniedException("引用所属知识空间不存在");
        Set<Long> chunkIds = hits.stream().map(ChunkHitVO::getChunkId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (chunkIds.contains(null)) throw new AccessDeniedException("引用缺少分块标识");
        Map<Long, KnowledgeChunk> chunks = chunkMapper.selectByIds(chunkIds).stream()
            .collect(Collectors.toMap(KnowledgeChunk::getChunkId, Function.identity()));
        Set<Long> docIds = chunks.values().stream().map(KnowledgeChunk::getDocId).collect(Collectors.toSet());
        Map<Long, KnowledgeDoc> docs = docMapper.selectByIds(docIds).stream()
            .collect(Collectors.toMap(KnowledgeDoc::getDocId, Function.identity()));
        for (ChunkHitVO hit : hits) {
            KnowledgeChunk chunk = chunks.get(hit.getChunkId());
            KnowledgeDoc doc = chunk == null ? null : docs.get(chunk.getDocId());
            if (chunk == null || !space.getSpaceId().equals(chunk.getSpaceId()) || doc == null
                || !Boolean.TRUE.equals(doc.getIsActive()) || !space.getSpaceId().equals(doc.getSpaceId())
                || (portalOnly && (!Boolean.TRUE.equals(doc.getPublicationManaged())
                    || !"published".equals(doc.getWorkflowStatus()) || !Boolean.TRUE.equals(doc.getOnline())))) {
                throw new AccessDeniedException("引用权限复核失败");
            }
            ContentSecurityService.Inspection inspection = contentSecurity.sanitizeReference(
                hit.getContent(), "chunk:" + hit.getChunkId());
            hit.setContent(inspection.sanitized());
        }
    }
}
