package com.zlz.agent.repository;

import java.util.List;

/**
 * 会话列表的保存与查询（先给内存实现，后续换 MySQL 只需换实现类）。
 */
public interface ChatHistoryRepository {

    /**
     * @param type   业务类型：chat / service / pdf
     * @param chatId 会话ID
     */
    void save(String type, String chatId);

    List<String> getChatIds(String type);
}
