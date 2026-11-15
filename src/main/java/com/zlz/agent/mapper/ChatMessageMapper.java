package com.zlz.agent.mapper;

import com.zlz.agent.entity.po.ChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper {

    @Insert("""
            INSERT INTO chat_message(conversation_id, msg_type, content)
            VALUES (#{conversationId}, #{msgType}, #{content})
            """)
    int insert(@Param("conversationId") String conversationId,
               @Param("msgType") String msgType,
               @Param("content") String content);

    /**
     * 取最近 limit 条，再按 id 正序返回（子查询先倒序取，外层再正序）。
     */
    @Select("""
            SELECT t.id, t.conversation_id AS conversationId, t.msg_type AS msgType,
                   t.content, t.create_time AS createTime
            FROM (SELECT id, conversation_id, msg_type, content, create_time
                  FROM chat_message
                  WHERE conversation_id = #{conversationId}
                  ORDER BY id DESC
                  LIMIT #{limit}) t
            ORDER BY t.id
            """)
    List<ChatMessage> selectRecent(@Param("conversationId") String conversationId,
                                   @Param("limit") int limit);

    @Select("""
            SELECT id, conversation_id AS conversationId, msg_type AS msgType,
                   content, create_time AS createTime
            FROM chat_message
            WHERE conversation_id = #{conversationId}
            ORDER BY id
            """)
    List<ChatMessage> selectAll(@Param("conversationId") String conversationId);

    @Select("SELECT COUNT(*) FROM chat_message WHERE conversation_id = #{conversationId}")
    int countByConversation(@Param("conversationId") String conversationId);

    /**
     * 摘要成功后，把已经进入摘要的旧消息删掉，避免原文无限增长。
     */
    @Delete("DELETE FROM chat_message WHERE conversation_id = #{conversationId} AND id <= #{maxId}")
    int deleteUpTo(@Param("conversationId") String conversationId,
                   @Param("maxId") Long maxId);

    @Delete("DELETE FROM chat_message WHERE conversation_id = #{conversationId}")
    int deleteByConversation(@Param("conversationId") String conversationId);
}
