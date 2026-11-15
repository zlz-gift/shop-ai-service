package com.zlz.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zlz.agent.entity.po.Product;

import java.util.List;

public interface IProductService extends IService<Product> {

    /**
     * 按关键词（商品名模糊）和分类查询，两个条件都可为空；最多返回 10 条。
     */
    List<Product> search(String keyword, String category);
}
