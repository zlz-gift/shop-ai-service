package com.zlz.agent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 内存向量库的落盘与预加载。
 * SimpleVectorStore 本身是内存实现，不处理这两步，重启就会把向量全丢。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreManager implements InitializingBean, DisposableBean {

    private final SimpleVectorStore vectorStore;

    @Value("${rag.store-path:./data/vector-store.json}")
    private String storePath;

    @Override
    public void afterPropertiesSet() {
        File file = new File(storePath);
        if (!file.exists()) {
            log.info("向量库文件不存在，启动为空库：{}", file.getAbsolutePath());
            return;
        }
        try {
            vectorStore.load(file);
            log.info("向量库预加载完成：{}（{} 字节）", file.getAbsolutePath(), file.length());
        } catch (Exception e) {
            log.warn("向量库预加载失败，本次以空库启动：{}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        saveNow();
    }

    /**
     * 每次上传文档后立即落盘，避免进程被强杀时丢掉刚入库的向量。
     */
    public void saveNow() {
        File file = new File(storePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.warn("向量库落盘目录创建失败：{}", parent.getAbsolutePath());
        }
        try {
            vectorStore.save(file);
            log.info("向量库已落盘：{}（{} 字节）", file.getAbsolutePath(), file.length());
        } catch (Exception e) {
            log.warn("向量库落盘失败：{}", e.getMessage());
        }
    }
}
