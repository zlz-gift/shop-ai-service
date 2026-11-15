package com.zlz.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不依赖网络与真实模型：用桩 EmbeddingModel + 捕获型 ChatModel，
 * 验证 QuestionAnswerAdvisor 是否真的把 FILTER_EXPRESSION 传到了检索里。
 */
class RagFilterOfflineTest {

    private static final String U1_TEXT = "退货运费由商家承担，质量问题可凭快递单报销";
    private static final String U2_TEXT = "换货仅支持同款同规格，不支持跨型号换货";

    @Test
    void filterExpressionIsAppliedToRetrieval() {
        SimpleVectorStore store = newStore();
        store.add(List.of(
                new Document(U1_TEXT, Map.of("userId", "u1")),
                new Document(U2_TEXT, Map.of("userId", "u2"))
        ));

        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(store)
                .searchRequest(SearchRequest.builder().topK(3).similarityThreshold(0.0).build())
                .build();

        // 对照组：不带过滤 —— 两篇文档都会进上下文，证明测试本身是有效的
        CapturingChatModel withoutFilter = new CapturingChatModel();
        ChatClient.builder(withoutFilter).defaultAdvisors(advisor).build()
                .prompt().user("退货运费谁承担").call().content();
        System.out.println("[无过滤] 含 u1 文档=" + withoutFilter.lastPrompt.contains("商家承担")
                + "，含 u2 文档=" + withoutFilter.lastPrompt.contains("跨型号"));

        // 实验组：带 userId 过滤 —— u2 的文档不应出现
        CapturingChatModel withFilter = new CapturingChatModel();
        ChatClient.builder(withFilter).defaultAdvisors(advisor).build()
                .prompt().user("退货运费谁承担")
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "userId == 'u1'"))
                .call().content();
        System.out.println("[有过滤] 含 u1 文档=" + withFilter.lastPrompt.contains("商家承担")
                + "，含 u2 文档=" + withFilter.lastPrompt.contains("跨型号"));

        assertTrue(withoutFilter.lastPrompt.contains("商家承担"), "对照组应检索到 u1 文档");
        assertTrue(withoutFilter.lastPrompt.contains("跨型号"), "对照组应检索到 u2 文档");
        assertTrue(withFilter.lastPrompt.contains("商家承担"), "过滤后仍应检索到 u1 文档");
        assertFalse(withFilter.lastPrompt.contains("跨型号"), "过滤后不应检索到 u2 的文档");
    }

    @Test
    void vectorStoreSurvivesSaveAndLoad() {
        SimpleVectorStore store = newStore();
        store.add(List.of(new Document(U1_TEXT, Map.of("userId", "u1"))));

        File file = new File(System.getProperty("java.io.tmpdir"), "vector-store-roundtrip.json");
        store.save(file);

        SimpleVectorStore reloaded = newStore();
        reloaded.load(file);
        List<Document> hits = reloaded.similaritySearch(
                SearchRequest.builder().query("退货运费").topK(1).similarityThreshold(0.0).build());

        System.out.println("[落盘回读] 命中=" + hits.size() + "，内容=" + (hits.isEmpty() ? "-" : hits.get(0).getText()));
        assertFalse(hits.isEmpty(), "重启后应从文件恢复出向量");
        assertTrue(hits.get(0).getText().contains("商家承担"));
    }

    private SimpleVectorStore newStore() {
        return SimpleVectorStore.builder(new StubEmbeddingModel()).build();
    }

    /**
     * 按字符哈希造 64 维向量：共字多的文本余弦相似度高，够用来验证检索与过滤。
     */
    private static float[] vec(String text) {
        float[] v = new float[64];
        for (char c : text.toCharArray()) {
            v[Math.floorMod(Character.hashCode(c), 64)] += 1f;
        }
        double norm = 0;
        for (float f : v) {
            norm += f * f;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
        }
        return v;
    }

    private static class StubEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> instructions = request.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                embeddings.add(new Embedding(vec(instructions.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vec(document.getText());
        }

        @Override
        public int dimensions() {
            return 64;
        }
    }

    private static class CapturingChatModel implements ChatModel {

        private String lastPrompt = "";

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt.getInstructions().stream()
                    .map(Message::getText)
                    .filter(t -> t != null)
                    .collect(Collectors.joining("\n"));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("OK"))));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().build();
        }
    }
}
