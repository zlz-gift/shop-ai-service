-- 演示用订单数据（主站 orders 表原本是空的，客服没法演示查单/退款）
-- 执行：mysql -u root -p -e "source demo-orders.sql"
-- 订单号故意用小号，方便对话里直接说 1001 / 1002

USE `java_shop`;

INSERT IGNORE INTO `orders` (`id`, `user_id`, `total_price`, `status`, `create_time`)
VALUES (1001, 2, 9498.00, '已发货', '2026-11-10 10:20:00'),
       (1002, 2, 4299.00, '已下单', '2026-11-12 15:05:00'),
       (1003, 2, 499.00, '已完成', '2026-10-28 09:30:00');

INSERT IGNORE INTO `order_items` (`id`, `order_id`, `product_id`, `price`, `quantity`)
VALUES (1, 1001, 1, 8999.00, 1),
       (2, 1001, 5, 499.00, 1),
       (3, 1002, 3, 4299.00, 1),
       (4, 1003, 5, 499.00, 1);
