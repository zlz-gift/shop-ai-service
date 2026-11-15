package com.zlz.agent.controller;

import com.zlz.agent.repository.ChatHistoryRepository;
import com.zlz.agent.util.ConversationIds;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ChatController {

    private static final String TYPE = "chat";

    private final ChatClient chatClient;
    private final ChatHistoryRepository chatHistoryRepository;

    /**
     * 通用对话，流式返回（本地 Ollama）。
     */
    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(String prompt, String chatId) {
        chatHistoryRepository.save(TYPE, chatId);
        return chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, ConversationIds.of(TYPE, chatId)))
                .stream()
                .content();
    }
}
