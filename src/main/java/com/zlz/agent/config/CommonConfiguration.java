package com.zlz.agent.config;

import com.zlz.agent.constants.SystemConstants;
import com.zlz.agent.model.FallbackChatModel;
import com.zlz.agent.tools.ShopTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfiguration {

    /**
     * 通用对话：本地 Ollama，不挂工具，用来验证流式输出。
     */
    @Bean
    public ChatClient chatClient(OllamaChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem(SystemConstants.CHAT_SYSTEM_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * 带降级的模型：优先云端百炼，异常回落本地 Ollama。
     */
    @Bean
    public ChatModel serviceChatModel(OpenAiChatModel primary, OllamaChatModel fallback) {
        return new FallbackChatModel(primary, fallback);
    }

    /**
     * 售后客服：云端百炼（可降级）+ 商城工具（查订单 / 查商品 / 提交退款）。
     */
    @Bean
    public ChatClient serviceChatClient(@Qualifier("serviceChatModel") ChatModel model,
                                        ChatMemory chatMemory,
                                        ShopTools shopTools) {
        return ChatClient.builder(model)
                .defaultSystem(SystemConstants.SERVICE_SYSTEM_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(shopTools)
                .build();
    }
}
