package com.kma.knowledge.rag.retrieve;

import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.entity.KnowledgeChunk;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.service.ModelProfileResolver;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 向量检索器
 *
 * @author party
 * @date 2026/06/30
 */
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
@RequiredArgsConstructor
public class VectorRetriever implements Retriever {

    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingQueryEncoder embeddingQueryEncoder;
    private final KnowledgeDatasetMapper datasetMapper;
    private final ModelProfileResolver profileResolver;

    @Override
    public List<ChunkHitVO> retrieve(RetrieveRequest request) {
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(request.getSpaceCode());
        if (space == null) {
            return new ArrayList<>();
        }
        int topK = request.getTopK() != null ? request.getTopK() : space.getDefaultTopK();
        int candidateK = topK * 2;

        float[] embedding = embeddingQueryEncoder.encode(request.getQuery(), space);
        KnowledgeDataset dataset = space.getDatasetId() == null ? null : datasetMapper.selectById(space.getDatasetId());
        String profileCode = dataset == null ? null : dataset.getEmbeddingProfileCode();
        int dimension = profileCode == null || profileCode.isBlank()
            ? space.getEmbeddingDim() : profileResolver.resolve(profileCode, "embedding").getDimension();
        List<KnowledgeChunk> chunks = chunkMapper.searchByVector(
            embedding, space.getSpaceId(), request.getSourceTags(), dimension, profileCode, request, candidateK);

        List<ChunkHitVO> hits = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            ChunkHitVO vo = toVo(chunk);
            vo.setSpaceId(space.getSpaceId());
            vo.setSpaceCode(space.getSpaceCode());
            vo.setSourceStage("vector");
            vo.setVectorScore(chunk.getRetrievalScore());
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



