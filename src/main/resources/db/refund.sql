-- 售后退款申请表
-- 建在电商主站同一个库 java_shop 里，与 orders / order_items / products 共用数据
-- 执行：mysql -u root -p -e "source refund.sql"

USE `java_shop`;

CREATE TABLE IF NOT EXISTS `refund_apply`
(
    `id`          INT          NOT NULL AUTO_INCREMENT COMMENT '主键，同时作为售后工单号',
    `order_id`    INT          NOT NULL COMMENT '订单ID',
    `reason`      VARCHAR(255) NOT NULL COMMENT '退款原因',
    `status`      VARCHAR(20)  NOT NULL DEFAULT '待处理' COMMENT '待处理/已同意/已拒绝',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',

    PRIMARY KEY (`id`),
    -- 一个订单只能有一条退款申请：并发重复提交由唯一索引兜底，
    -- 应用层用 INSERT IGNORE 靠影响行数判断，避免"先查后插"的竞态
    UNIQUE KEY `uk_refund_order` (`order_id`),
    CONSTRAINT `fk_refund_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '售后退款申请表';
