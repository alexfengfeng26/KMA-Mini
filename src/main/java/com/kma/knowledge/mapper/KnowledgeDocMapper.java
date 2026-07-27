package com.kma.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kma.knowledge.entity.KnowledgeDoc;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识文档 Mapper
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {

    /**
     * 更新文档解析状态
     */
    @Update("UPDATE knowledge_doc SET parse_status = #{status}, chunk_count = #{chunkCount}, error_message = #{errorMessage}, update_time = now() WHERE doc_id = #{docId}")
    int updateParseStatus(@Param("docId") Long docId,
                          @Param("status") String status,
                          @Param("chunkCount") Integer chunkCount,
                          @Param("errorMessage") String errorMessage);

    /**
     * 删除 N 天前的失败文档（级联删除分块）
     */
    @Delete("DELETE FROM knowledge_doc WHERE parse_status = 'failed' AND update_time < now() - interval '1 day' * #{days}")
    int deleteFailedDocsOlderThan(@Param("days") int days);

    /**
     * 查询空间下所有文档
     */
    @Select("SELECT * FROM knowledge_doc WHERE space_id = #{spaceId}")
    List<KnowledgeDoc> selectBySpaceId(@Param("spaceId") Long spaceId);

    /**
     * 在版本激活事务内锁定同一外部来源的最新版本，防止较慢的旧任务反向覆盖新版本。
     */
    @Select("""
        SELECT source_version
        FROM knowledge_doc
        WHERE space_id = #{spaceId} AND external_ref = #{externalRef}
        ORDER BY source_version DESC
        LIMIT 1
        FOR UPDATE
        """)
    Long selectLatestSourceVersionForUpdate(@Param("spaceId") Long spaceId,
                                            @Param("externalRef") String externalRef);
}



