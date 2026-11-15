package com.zlz.agent.service;

import com.zlz.agent.entity.po.RefundApply;

public interface IAfterSaleService {

    /**
     * 提交退款申请。返回给模型看的自然语言结果（成功带工单号，失败说明原因）。
     */
    String applyRefund(Integer orderId, String reason);

    RefundApply findByOrderId(Integer orderId);
}
