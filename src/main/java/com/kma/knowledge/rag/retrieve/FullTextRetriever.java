package com.kma.knowledge.rag.retrieve;

import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.entity.KnowledgeChunk;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 全文检索器（PostgreSQL tsvector）
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
@RequiredArgsConstructor
public class FullTextRetriever implements Retriever {

    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final LexicalQueryAnalyzer lexicalQueryAnalyzer;

    @Override
    public List<ChunkHitVO> retrieve(RetrieveRequest request) {
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(request.getSpaceCode());
        if (space == null) {
            return new ArrayList<>();
        }
        int topK = request.getTopK() != null ? request.getTopK() : space.getDefaultTopK();
        int candidateK = topK * 2;

        String tsQuery = lexicalQueryAnalyzer.toTsQuery(request.getQuery());
        if (tsQuery.isBlank()) {
            return new ArrayList<>();
        }
        List<KnowledgeChunk> chunks = chunkMapper.searchByFullText(tsQuery, space.getSpaceId(), request.getSourceTags(), request, candidateK);
        List<ChunkHitVO> hits = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            ChunkHitVO vo = toVo(chunk);
            vo.setSpaceId(space.getSpaceId());
            vo.setSpaceCode(space.getSpaceCode());
            vo.setSourceStage("fulltext");
            vo.setFullTextScore(chunk.getRetrievalScore());
            vo.setScore(chunk.getRetrievalScore());
            hits.add(vo);
        }
        return hits;
    }

    private ChunkHitVO toVo(KnowledgeChunk chunk) {
        ChunkHitVO vo = new ChunkHitVO();
        vo.setChunkId(chunk.getChunkId());
        vo.setDocId(chunk.getDocId());
        vo.setDocTitle(chunk.getDocTitle());
        vo.setExternalRef(chunk.getExternalRef());
        vo.setSourceTag(chunk.getSourceTag());
        vo.setContent(chunk.getContent());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setMeta(chunk.getMeta());
        vo.setDocumentNumber(chunk.getDocumentNumber());
        vo.setIssuingAuthority(chunk.getIssuingAuthority());
        vo.setPublishDate(chunk.getPublishDate());
        vo.setValidityStatus(chunk.getValidityStatus());
        vo.setSection("第 " + (chunk.getChunkIndex() + 1) + " 节");
        return vo;
    }
}



