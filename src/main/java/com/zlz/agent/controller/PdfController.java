package com.zlz.agent.controller;

import com.zlz.agent.rag.PdfKnowledgeService;
import com.zlz.agent.repository.ChatHistoryRepository;
import com.zlz.agent.util.ConversationIds;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/ai/pdf")
@RequiredArgsConstructor
public class PdfController {

    private static final String TYPE = "pdf";

    /**
     * 检索参数：阈值 0.6 过滤掉明显不相关的分片，topK=2 只把最像的两片塞进上下文。
     * 这两个数是按样例效果调的，不是评测结论。
     */
    private static final double SIMILARITY_THRESHOLD = 0.6;
    private static final int TOP_K = 2;

    private final ChatClient pdfChatClient;
    private final PdfKnowledgeService pdfKnowledgeService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final VectorStore vectorStore;

    @PostMapping(value = "/upload", produces = "application/json;charset=utf-8")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "demo-user") String userId) throws IOException {
        if (file.isEmpty()) {
            return Map.of("success", false, "message", "文件为空");
        }
        String fileName = file.getOriginalFilename() == null ? "unknown.pdf" : file.getOriginalFilename();
        int chunks = pdfKnowledgeService.ingest(new ByteArrayResource(file.getBytes()), userId, fileName);
        return Map.of("success", true, "fileName", fileName, "userId", userId, "chunks", chunks);
    }

    @PostMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(String prompt, String chatId,
                             @RequestParam(defaultValue = "demo-user") String userId) {
        chatHistoryRepository.save(TYPE, chatId);

        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .topK(TOP_K)
                        .build())
                .build();

        String filter = pdfKnowledgeService.filterExpression(userId);
        return pdfChatClient.prompt()
                .user(prompt)
                .advisors(advisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, ConversationIds.of(TYPE, chatId))
                        .param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filter))
                .stream()
                .content();
    }
}
