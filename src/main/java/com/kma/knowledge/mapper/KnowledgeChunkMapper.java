package com.kma.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kma.knowledge.entity.KnowledgeChunk;
import com.kma.knowledge.dto.RetrieveRequest;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识分块 Mapper
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 根据文档 ID 删除分块
     */
    int deleteByDocId(@Param("docId") Long docId);

    /**
     * 向量相似度检索（余弦距离）
     */
    List<KnowledgeChunk> searchByVector(@Param("embedding") float[] embedding,
                                        @Param("spaceId") Long spaceId,
                                        @Param("sourceTags") List<String> sourceTags,
                                        @Param("dimension") int dimension,
                                        @Param("profileCode") String profileCode,
                                        @Param("filters") RetrieveRequest filters,
                                        @Param("limit") int limit);

    /**
     * 全文检索（PostgreSQL tsvector）
     */
    List<KnowledgeChunk> searchByFullText(@Param("query") String query,
                                          @Param("spaceId") Long spaceId,
                                          @Param("sourceTags") List<String> sourceTags,
                                          @Param("filters") RetrieveRequest filters,
                                          @Param("limit") int limit);

    /**
     * 清理孤儿分块（不存在对应文档）
     */
    @Delete("DELETE FROM knowledge_chunk WHERE doc_id NOT IN (SELECT doc_id FROM knowledge_doc)")
    int deleteOrphanChunks();
}



