package com.zlz.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zlz.agent.entity.po.Order;
import com.zlz.agent.entity.vo.OrderDetailVO;

public interface IOrderService extends IService<Order> {

    /**
     * 查订单详情，订单不存在返回 null。
     */
    OrderDetailVO detail(Integer orderId);
}
