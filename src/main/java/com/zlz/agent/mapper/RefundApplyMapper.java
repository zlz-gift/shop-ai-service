package com.zlz.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlz.agent.entity.po.RefundApply;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface RefundApplyMapper extends BaseMapper<RefundApply> {

    /**
     * 依赖 order_id 唯一索引做幂等：重复提交时影响行数为 0，不抛异常。
     * 不用"先查再插"，那条路在两个并发请求下都会查不到。
     *
     * @return 1=插入成功，0=该订单已有退款申请
     */
    @Insert("""
            INSERT IGNORE INTO refund_apply(order_id, reason, status)
            VALUES (#{orderId}, #{reason}, '待处理')
            """)
    int insertIgnore(@Param("orderId") Integer orderId, @Param("reason") String reason);
}
