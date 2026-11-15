package com.zlz.agent.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 带降级的模型包装：优先走云端（百炼），异常时回落本地 Ollama。
 * <p>
 * 降级时丢掉 ChatOptions（也就丢掉了工具定义），退化成纯对话——
 * 因为本地小模型不支持工具调用，带着工具再请求一次只会继续报错。
 */
@Slf4j
public class FallbackChatModel implements ChatModel {

    private final ChatModel primary;

    private final ChatModel fallback;

    public FallbackChatModel(ChatModel primary, ChatModel fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            return primary.call(prompt);
        } catch (Exception e) {
            log.warn("主模型调用失败，降级为本地模型纯对话：{}", e.getMessage());
            return fallback.call(new Prompt(prompt.getInstructions()));
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        AtomicBoolean emitted = new AtomicBoolean(false);
        return primary.stream(prompt)
                .doOnNext(response -> emitted.set(true))
                .onErrorResume(e -> {
                    // 已经吐字了就不能重放：否则前端会看到半句话 + 从头再来一遍
                    if (emitted.get()) {
                        log.warn("流式输出中途失败，已输出内容无法重放：{}", e.getMessage());
                        return Flux.error(e);
                    }
                    log.warn("主模型流式调用失败，降级为本地模型纯对话：{}", e.getMessage());
                    return fallback.stream(new Prompt(prompt.getInstructions()));
                });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return primary.getDefaultOptions();
    }
}
