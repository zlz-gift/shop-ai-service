package com.zlz.agent.entity.vo;

import com.zlz.agent.entity.po.Product;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品对外视图：不把库存、版本号、图片地址塞进模型上下文。
 */
@Data
public class ProductVO {

    private String name;

    private String category;

    private BigDecimal price;

    private String description;

    public static ProductVO from(Product p) {
        ProductVO vo = new ProductVO();
        vo.setName(p.getName());
        vo.setCategory(p.getCategory());
        vo.setPrice(p.getPrice());
        vo.setDescription(p.getDescription());
        return vo;
    }
}
