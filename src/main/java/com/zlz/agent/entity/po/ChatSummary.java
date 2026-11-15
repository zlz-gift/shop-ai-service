package com.zlz.agent.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 会话摘要（长期记忆），一个会话只有一条，内容由 LLM 逐步合并。
 */
@Data
@Accessors(chain = true)
@TableName("chat_summary")
public class ChatSummary {

    @TableId(value = "conversation_id", type = IdType.INPUT)
    private String conversationId;

    private String content;

    private LocalDateTime updateTime;
}
