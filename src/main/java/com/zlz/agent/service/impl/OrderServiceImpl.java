package com.zlz.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zlz.agent.entity.po.Order;
import com.zlz.agent.entity.vo.OrderDetailVO;
import com.zlz.agent.mapper.OrderMapper;
import com.zlz.agent.service.IOrderService;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    @Override
    public OrderDetailVO detail(Integer orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return null;
        }
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId());
        vo.setStatus(order.getStatus());
        vo.setTotalPrice(order.getTotalPrice());
        vo.setCreateTime(order.getCreateTime());
        vo.setItems(baseMapper.selectItemRows(orderId).stream().map(row -> {
            OrderDetailVO.Item item = new OrderDetailVO.Item();
            item.setProductName(row.getProductName());
            item.setPrice(row.getPrice());
            item.setQuantity(row.getQuantity());
            return item;
        }).toList());
        return vo;
    }
}
