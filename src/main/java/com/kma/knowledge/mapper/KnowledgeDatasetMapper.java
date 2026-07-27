package com.kma.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kma.knowledge.entity.KnowledgeDataset;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据集 Mapper
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeDatasetMapper extends BaseMapper<KnowledgeDataset> {
    @Select("SELECT embedding_profile_code FROM knowledge_dataset WHERE dataset_id=#{datasetId} FOR UPDATE")
    String selectEmbeddingProfileForUpdate(@Param("datasetId") Long datasetId);
}



