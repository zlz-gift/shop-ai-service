package com.zlz.agent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 入库：解析 → 切分 → 打上用户标记 → 向量化入库 → 落盘。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfKnowledgeService {

    private static final String USER_ID = "userId";

    /**
     * 中文按句切：300 字一片、最少 80 字、以中文标点断句。
     * 默认 800 token 一片对这种政策文档太粗，topK=2 时容易把整页糊成一团。
     */
    private static final TokenTextSplitter SPLITTER = TokenTextSplitter.builder()
            .withChunkSize(300)
            .withMinChunkSizeChars(80)
            .withMinChunkLengthToEmbed(20)
            .withMaxNumChunks(500)
            .withKeepSeparator(true)
            .withPunctuationMarks(List.of('。', '；', '！', '？', '\n'))
            .build();

    private final VectorStore vectorStore;

    private final VectorStoreManager vectorStoreManager;

    /**
     * @return 入库的分片数
     */
    public int ingest(Resource resource, String userId, String fileName) {
        List<Document> pages = new PagePdfDocumentReader(resource).get();
        List<Document> chunks = SPLITTER.apply(pages);

        List<Document> tagged = chunks.stream().map(chunk -> {
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put(USER_ID, userId);
            metadata.put("fileName", fileName);
            return new Document(chunk.getText(), metadata);
        }).toList();

        vectorStore.add(tagged);
        vectorStoreManager.saveNow();
        log.info("PDF 入库完成：{}，用户={}，页数={}，分片数={}", fileName, userId, pages.size(), tagged.size());
        return tagged.size();
    }

    /**
     * 检索时的元数据过滤条件。
     * userId 会被拼进过滤表达式，所以必须先做格式校验，避免表达式注入。
     */
    public String filterExpression(String userId) {
        if (userId == null || !userId.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("userId 只允许字母、数字、下划线和短横线，长度 1-32");
        }
        return USER_ID + " == '" + userId + "'";
    }
}
