package com.zlz.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zlz.agent.entity.po.Order;
import com.zlz.agent.entity.po.RefundApply;
import com.zlz.agent.mapper.OrderMapper;
import com.zlz.agent.mapper.RefundApplyMapper;
import com.zlz.agent.service.IAfterSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements IAfterSaleService {

    /**
     * 只有这两种状态可以发起退款：已完成（已签收）与已取消不允许。
     */
    private static final Set<String> REFUNDABLE = Set.of("已下单", "已发货");

    private final OrderMapper orderMapper;
    private final RefundApplyMapper refundApplyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String applyRefund(Integer orderId, String reason) {
        if (orderId == null || reason == null || reason.isBlank()) {
            return "缺少订单号或退款原因，请向用户补充后再提交";
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return "订单号 " + orderId + " 不存在，请让用户核对订单号后重试";
        }
        if (!REFUNDABLE.contains(order.getStatus())) {
            return "订单当前状态为【" + order.getStatus() + "】，不支持退款";
        }

        // 唯一索引 + INSERT IGNORE：并发下只有一个请求能插入成功，另一个拿到 0 行
        int rows = refundApplyMapper.insertIgnore(orderId, reason);
        if (rows == 0) {
            RefundApply exist = findByOrderId(orderId);
            return "该订单已提交过退款申请，工单号：" + (exist == null ? "查询中" : exist.getId()) + "，无需重复提交";
        }

        RefundApply apply = findByOrderId(orderId);
        return "退款申请提交成功，售后工单号：" + apply.getId()
                + "，当前状态：待处理，退款将按原支付方式退回。";
    }

    @Override
    public RefundApply findByOrderId(Integer orderId) {
        return refundApplyMapper.selectOne(
                Wrappers.<RefundApply>lambdaQuery().eq(RefundApply::getOrderId, orderId));
    }
}
