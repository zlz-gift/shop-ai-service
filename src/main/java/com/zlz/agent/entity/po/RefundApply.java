package com.zlz.agent.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 售后退款申请（新增表，order_id 上有唯一索引，一个订单只能申请一次）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("refund_apply")
public class RefundApply implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，同时作为对外展示的售后工单号
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer orderId;

    private String reason;

    /**
     * 待处理 / 已同意 / 已拒绝
     */
    private String status;

    private LocalDateTime createTime;
}
