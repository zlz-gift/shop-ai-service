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
public class CustomerServiceController {

    private static final String TYPE = "service";

    private final ChatClient serviceChatClient;
    private final ChatHistoryRepository chatHistoryRepository;

    /**
     * 售后客服，流式返回（云端百炼 + 商城工具）。
     */
    @PostMapping(value = "/service", produces = "text/html;charset=utf-8")
    public Flux<String> service(String prompt, String chatId) {
        chatHistoryRepository.save(TYPE, chatId);
        return serviceChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, ConversationIds.of(TYPE, chatId)))
                .stream()
                .content();
    }
}
