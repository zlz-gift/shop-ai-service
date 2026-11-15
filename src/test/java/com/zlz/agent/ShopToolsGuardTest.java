package com.zlz.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlz.agent.service.IAfterSaleService;
import com.zlz.agent.service.IOrderService;
import com.zlz.agent.service.IProductService;
import com.zlz.agent.tools.ShopTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工具异常兜底：数据库/下游抛异常时，工具必须返回可读文本而不是把异常抛进对话链路。
 * 纯单元测试，不依赖 MySQL 与模型。
 */
@DisplayName("工具执行异常兜底")
class ShopToolsGuardTest {

    @Test
    @DisplayName("查订单抛异常时返回可读提示，且不泄露堆栈")
    void queryOrderShouldNotPropagateException() {
        IOrderService orderService = mock(IOrderService.class);
        when(orderService.detail(any())).thenThrow(new IllegalStateException("connection reset by peer"));

        ShopTools tools = new ShopTools(orderService, mock(IProductService.class),
                mock(IAfterSaleService.class), new ObjectMapper());

        String result = tools.queryOrder(1001);

        System.out.println("[兜底返回] " + result);
        assertTrue(result.contains("queryOrder"), "应说明是哪个工具出错");
        assertTrue(result.contains("IllegalStateException"), "应带上异常类型，便于模型判断是否重试");
        assertFalse(result.contains("at com.zlz"), "不能把堆栈塞进对话上下文");
    }

    @Test
    @DisplayName("查商品抛异常时同样兜住")
    void queryProductsShouldNotPropagateException() {
        IProductService productService = mock(IProductService.class);
        when(productService.search(any(), any())).thenThrow(new RuntimeException("db down"));

        ShopTools tools = new ShopTools(mock(IOrderService.class), productService,
                mock(IAfterSaleService.class), new ObjectMapper());

        String result = tools.queryProducts("手机", null);

        System.out.println("[兜底返回] " + result);
        assertTrue(result.contains("queryProducts"));
        assertTrue(result.contains("RuntimeException"));
    }
}
