package com.zlz.agent.controller;

import com.zlz.agent.entity.vo.MessageVo;
import com.zlz.agent.repository.ChatHistoryRepository;
import com.zlz.agent.util.ConversationIds;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/history")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatMemory chatMemory;

    /**
     * 查询某业务下的会话列表。
     *
     * @param type 业务类型：chat / service / pdf
     */
    @GetMapping("/{type}")
    public List<String> getChatIds(@PathVariable String type) {
        return chatHistoryRepository.getChatIds(type);
    }

    /**
     * 查询某个会话的历史消息。
     * 记忆里会带一条「长期摘要」的 SystemMessage，那是给模型看的，不回给前端。
     */
    @GetMapping("/{type}/{chatId}")
    public List<MessageVo> getChatHistory(@PathVariable String type, @PathVariable String chatId) {
        List<Message> messages = chatMemory.get(ConversationIds.of(type, chatId));
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
                .filter(m -> m.getMessageType() != MessageType.SYSTEM)
                .map(MessageVo::new)
                .toList();
    }
}
