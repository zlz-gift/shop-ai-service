package com.zlz.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlz.agent.entity.vo.OrderDetailVO;
import com.zlz.agent.entity.vo.ProductVO;
import com.zlz.agent.service.IAfterSaleService;
import com.zlz.agent.service.IOrderService;
import com.zlz.agent.service.IProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交给模型自主调用的商城工具。
 * 返回统一用 String：JSON 给模型做结构化推理，失败时给自然语言原因，
 * 这样工具内部的异常不会直接抛进对话链路里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopTools {

    private final IOrderService orderService;
    private final IProductService productService;
    private final IAfterSaleService afterSaleService;
    private final ObjectMapper objectMapper;

    @Tool(description = "根据订单号查询订单详情：订单状态、总金额、下单时间和商品明细（名称/单价/数量）")
    public String queryOrder(
            @ToolParam(description = "订单号，纯数字，例如 1001") Integer orderId) {
        OrderDetailVO detail = orderService.detail(orderId);
        if (detail == null) {
            return "未查询到订单号为 " + orderId + " 的订单，请让用户核对订单号后重试";
        }
        return toJson(detail);
    }

    @Tool(description = "按商品名称关键词或分类查询在售商品，用于回答商品相关咨询；两个参数都可以不传，最多返回 10 条")
    public String queryProducts(
            @ToolParam(description = "商品名称关键词，可为空", required = false) String keyword,
            @ToolParam(description = "商品分类，可为空", required = false) String category) {
        List<ProductVO> products = productService.search(keyword, category).stream()
                .map(ProductVO::from)
                .toList();
        if (products.isEmpty()) {
            return "没有找到符合条件的商品，请让用户换个关键词或分类再试";
        }
        return toJson(products);
    }

    @Tool(description = "提交退款申请并返回售后工单号。只有当用户明确要求退款、提供了订单号、并说明了退款原因时才调用")
    public String applyRefund(
            @ToolParam(description = "订单号，纯数字") Integer orderId,
            @ToolParam(description = "退款原因，用一句话概括用户给出的理由") String reason) {
        return afterSaleService.applyRefund(orderId, reason);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("工具返回值序列化失败: {}", e.getMessage());
            return String.valueOf(value);
        }
    }
}
