package com.kma.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kma.knowledge.entity.KnowledgeChatMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 问答会话消息 Mapper
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeChatMessageMapper extends BaseMapper<KnowledgeChatMessage> {

    /**
     * 查询会话最近 N 条消息
     */
    @Select("SELECT * FROM knowledge_chat_message WHERE session_id = #{sessionId} ORDER BY create_time DESC LIMIT #{limit}")
    List<KnowledgeChatMessage> selectRecentBySession(@Param("sessionId") Long sessionId, @Param("limit") int limit);
}



