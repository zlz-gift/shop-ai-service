-- 分层会话记忆的持久化表
-- 短期：chat_message 保存原文，只取最近 N 条进上下文
-- 长期：chat_summary 保存 LLM 压缩后的摘要，按会话一条
-- 执行：mysql -u root -p -e "source chat_memory.sql"

USE `java_shop`;

CREATE TABLE IF NOT EXISTS `chat_message`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `conversation_id` VARCHAR(128) NOT NULL COMMENT '业务类型:chatId',
    `msg_type`        VARCHAR(20)  NOT NULL COMMENT 'user / assistant',
    `content`         TEXT         NOT NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    -- 按会话取最近 N 条、按会话裁剪旧消息都走这个索引
    KEY `idx_chat_message_conv` (`conversation_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '会话原文（短期记忆）';

CREATE TABLE IF NOT EXISTS `chat_summary`
(
    `conversation_id` VARCHAR(128) NOT NULL COMMENT '业务类型:chatId',
    `content`         TEXT         NOT NULL COMMENT 'LLM 压缩后的长期摘要',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`conversation_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '会话摘要（长期记忆）';
