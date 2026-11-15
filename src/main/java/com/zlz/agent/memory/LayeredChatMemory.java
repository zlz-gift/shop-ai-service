package com.zlz.agent.memory;

import com.zlz.agent.entity.po.ChatMessage;
import com.zlz.agent.entity.po.ChatSummary;
import com.zlz.agent.mapper.ChatMessageMapper;
import com.zlz.agent.mapper.ChatSummaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 分层会话记忆：
 * <ul>
 *   <li>短期：原文存 chat_message，每轮只取最近 {@link #KEEP} 条进上下文</li>
 *   <li>长期：累计超过 {@link #TRIGGER} 条时，把超出窗口的部分交给 LLM 压成摘要，存 chat_summary</li>
 * </ul>
 * KEEP 与 TRIGGER 之间的 10 条是缓冲带：如果两者相等，每轮都会触发压缩，成本翻倍。
 * <p>
 * 状态全部落在 MySQL 里，不在内存里攒，所以重启不丢、多实例也能共用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayeredChatMemory implements ChatMemory {

    /**
     * 压缩后保留在上下文中的原文条数
     */
    private static final int KEEP = 20;

    /**
     * 累计条数超过它就触发一次压缩
     */
    private static final int TRIGGER = 30;

    private static final String SUMMARY_PROMPT = """
            你是对话摘要助手。请把下面的对话提炼成一段简洁、客观的摘要，
            保留关键信息：用户提到的事实、偏好、待办事项、重要时间以及已经确认的结论。
            用第三人称、平实准确，不要啰嗦，不要加入主观评价，不超过 200 字。
            """;

    private final ChatMessageMapper messageMapper;
    private final ChatSummaryMapper summaryMapper;
    /**
     * 用 ObjectProvider 延迟取模型，避免 ChatMemory ← ChatClient 的循环依赖
     */
    private final ObjectProvider<OpenAiChatModel> summaryModel;

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> storable = messages.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                // 工具调用的中间消息（只有 tool_calls、没有正文）不进历史，否则摘要会被工具原始返回污染
                .filter(m -> StringUtils.hasText(m.getText()))
                .toList();
        if (storable.isEmpty()) {
            return;
        }
        storable.forEach(m -> messageMapper.insert(conversationId, typeOf(m), m.getText()));
        compressIfNeeded(conversationId);
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> result = new ArrayList<>();
        ChatSummary summary = summaryMapper.selectById(conversationId);
        if (summary != null && StringUtils.hasText(summary.getContent())) {
            result.add(new SystemMessage("【之前对话的摘要（长期记忆）】\n" + summary.getContent()));
        }
        messageMapper.selectRecent(conversationId, KEEP).forEach(row -> result.add(toMessage(row)));
        return result;
    }

    @Override
    public void clear(String conversationId) {
        messageMapper.deleteByConversation(conversationId);
        summaryMapper.deleteById(conversationId);
        log.debug("会话 {} 的记忆已清空", conversationId);
    }

    private void compressIfNeeded(String conversationId) {
        int total = messageMapper.countByConversation(conversationId);
        if (total <= TRIGGER) {
            return;
        }
        try {
            List<ChatMessage> all = messageMapper.selectAll(conversationId);
            int cut = all.size() - KEEP;
            if (cut <= 0) {
                return;
            }
            List<ChatMessage> older = all.subList(0, cut);
            ChatSummary existing = summaryMapper.selectById(conversationId);
            String merged = summarize(existing == null ? "" : existing.getContent(), older);
            if (!StringUtils.hasText(merged)) {
                log.warn("会话 {} 摘要结果为空，保留原文", conversationId);
                return;
            }
            upsertSummary(conversationId, merged);
            messageMapper.deleteUpTo(conversationId, older.get(older.size() - 1).getId());
            log.debug("会话 {} 压缩：{} 条原文 -> 摘要 {} 字，保留 {} 条", conversationId, older.size(), merged.length(),
                    messageMapper.countByConversation(conversationId));
        } catch (Exception e) {
            // 压缩失败不能让本轮回复失败，也不能让缓冲区无限增长：保留原文，下一轮会再次尝试
            log.warn("会话 {} 摘要压缩失败，本轮跳过：{}", conversationId, e.getMessage());
        }
    }

    private String summarize(String previousSummary, List<ChatMessage> messages) {
        OpenAiChatModel model = summaryModel.getIfAvailable();
        if (model == null) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (ChatMessage m : messages) {
            joiner.add(("user".equals(m.getMsgType()) ? "用户: " : "助手: ") + m.getContent());
        }
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SUMMARY_PROMPT),
                new UserMessage("已有摘要：\n" + (StringUtils.hasText(previousSummary) ? previousSummary : "（无）")
                        + "\n\n待总结的新对话：\n" + joiner)
        ));
        ChatResponse response = model.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private void upsertSummary(String conversationId, String content) {
        ChatSummary summary = summaryMapper.selectById(conversationId);
        if (summary == null) {
            summaryMapper.insert(new ChatSummary().setConversationId(conversationId).setContent(content));
        } else {
            summaryMapper.updateById(new ChatSummary().setConversationId(conversationId).setContent(content));
        }
    }

    private Message toMessage(ChatMessage row) {
        return switch (row.getMsgType()) {
            case "user" -> new UserMessage(row.getContent());
            case "assistant" -> new AssistantMessage(row.getContent());
            default -> new SystemMessage(row.getContent());
        };
    }

    private String typeOf(Message message) {
        return message.getMessageType() == MessageType.USER ? "user" : "assistant";
    }
}
