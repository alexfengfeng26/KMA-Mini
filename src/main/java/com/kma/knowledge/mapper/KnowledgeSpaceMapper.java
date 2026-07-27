package com.kma.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kma.knowledge.entity.KnowledgeSpace;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识空间 Mapper
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeSpaceMapper extends BaseMapper<KnowledgeSpace> {

    /**
     * 根据 spaceCode 查询空间
     */
    @Select("SELECT * FROM knowledge_space WHERE space_code = #{spaceCode} LIMIT 1")
    KnowledgeSpace selectBySpaceCode(@Param("spaceCode") String spaceCode);
}



