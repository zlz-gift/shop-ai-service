package com.zlz.agent.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存实现：容器自己持有 map，不再通过构造注入 Map（原来注入的 Map 可能不可变，put 会抛异常）。
 * 重启后会话列表会丢，后续换成 chat_session 表实现。
 */
@Slf4j
@Component
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {

    private final Map<String, List<String>> chatHistory = new ConcurrentHashMap<>();

    @Override
    public void save(String type, String chatId) {
        if (type == null || chatId == null || chatId.isBlank()) {
            return;
        }
        List<String> chatIds = chatHistory.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        if (!chatIds.contains(chatId)) {
            chatIds.add(chatId);
        }
        log.debug("会话登记: type={}, chatId={}, 当前{}条", type, chatId, chatIds.size());
    }

    @Override
    public List<String> getChatIds(String type) {
        return Collections.unmodifiableList(chatHistory.getOrDefault(type, List.of()));
    }
}
