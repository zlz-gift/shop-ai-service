package com.zlz.agent.entity.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;

/**
 * 把 Spring AI 的 Message 转成前端能直接渲染的 {role, content}。
 */
@Data
@NoArgsConstructor
public class MessageVo {

    private String role;

    private String content;

    public MessageVo(Message msg) {
        this.content = msg.getText();
        this.role = switch (msg.getMessageType()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            default -> "";
        };
    }
}
