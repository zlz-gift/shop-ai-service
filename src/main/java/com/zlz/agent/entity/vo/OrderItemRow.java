package com.zlz.agent.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 一条 JOIN 查询的结果行：orders + order_items + products 三表关联，
 * 避免"先查订单、再循环查明细"的 N+1。
 */
@Data
public class OrderItemRow {

    private String productName;

    private BigDecimal price;

    private Integer quantity;
}
