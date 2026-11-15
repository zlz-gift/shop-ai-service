package com.zlz.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zlz.agent.entity.po.Product;
import com.zlz.agent.mapper.ProductMapper;
import com.zlz.agent.service.IProductService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    @Override
    public List<Product> search(String keyword, String category) {
        return lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Product::getName, keyword)
                .eq(category != null && !category.isBlank(), Product::getCategory, category)
                // 工具返回值要进模型的上下文，限制条数，避免把整张表灌给模型
                .last("LIMIT 10")
                .list();
    }
}
