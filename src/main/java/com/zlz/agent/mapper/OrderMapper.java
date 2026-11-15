package com.zlz.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlz.agent.entity.po.Order;
import com.zlz.agent.entity.vo.OrderItemRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 一次 JOIN 取回订单明细，避免按明细逐条查商品的 N+1。
     */
    @Select("""
            SELECT p.name        AS productName,
                   oi.price      AS price,
                   oi.quantity   AS quantity
            FROM order_items oi
                     JOIN products p ON p.id = oi.product_id
            WHERE oi.order_id = #{orderId}
            ORDER BY oi.id
            """)
    List<OrderItemRow> selectItemRows(@Param("orderId") Integer orderId);
}
