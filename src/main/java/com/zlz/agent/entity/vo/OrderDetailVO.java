package com.zlz.agent.entity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情，作为工具返回值交给模型。
 * 只保留对外可说的字段，user_id、库存等内部字段一律不出口。
 */
@Data
public class OrderDetailVO {

    private Integer orderId;

    private String status;

    private BigDecimal totalPrice;

    private LocalDateTime createTime;

    private List<Item> items;

    @Data
    public static class Item {
        private String productName;
        private BigDecimal price;
        private Integer quantity;
    }
}
