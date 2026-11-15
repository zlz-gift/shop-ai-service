package com.zlz.agent.util;

/**
 * 会话记忆的隔离键：不同业务用同一个 chatId 时不能共用上下文，
 * 统一拼成 "type:chatId"，写入和读取都必须走这里。
 */
public final class ConversationIds {

    private ConversationIds() {
    }

    public static String of(String type, String chatId) {
        return type + ":" + chatId;
    }
}
