package com.zlz.agent.entity.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话原文（短期记忆）。这里用注解 SQL 直接读写，不走 MyBatis-Plus 的 BaseMapper。
 */
@Data
public class ChatMessage {

    private Long id;

    private String conversationId;

    private String msgType;

    private String content;

    private LocalDateTime createTime;
}
