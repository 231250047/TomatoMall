package com.example.tomatomall.service;

import com.example.tomatomall.vo.ProductVO;
import java.util.List;

/**
 * 商品缓存服务接口
 * 
 * 【Redis 数据结构选型�?
 * 使用 Hash 存储商品详情，原因:
 * 1. 商品有多个字段(标题、价格、描述等)，Hash 可以存储对象的多个属�?
 * 2. 可以单独获取/更新某个字段，无需读取整个对象
 * 3. 相比 String 存储 JSON，Hash 更节省空间(共享对象头)
 * 
 * 【缓�?Key 设计�?
 * - 商品详情:product:detail:{productId}
 * - 商品列表:product:list:all
 * 
 * 【面试要点�?
 * - Hash vs String 存储对象的优�?
 * - 缓存更新策略:Cache Aside Pattern
 */
public interface ProductCacheService {
    
    /**
     * 从缓存获取商品详�?
     * @param productId 商品ID
     * @return 商品VO，缓存未命中返回 null
     */
    ProductVO getProductFromCache(String productId);
    
    /**
     * 将商品详情存入缓�?
     * @param productVO 商品VO
     */
    void setProductToCache(ProductVO productVO);
    
    /**
     * 删除商品缓存
     * 【使用场景】商品信息更新后，删除缓存保证一致�?
     * @param productId 商品ID
     */
    void deleteProductCache(String productId);
    
    /**
     * 获取商品详情(优先缓存)
     * 【缓存策略】Cache Aside Pattern
     * 1. 先查缓存，命中则返回
     * 2. 缓存未命中，查数据库
     * 3. 将数据库结果写入缓存
     * 
     * @param productId 商品ID
     * @return 商品VO
     */
    ProductVO getProductWithCache(String productId);
    
    /**
     * 记录商品浏览，用于热度统�?
     * @param productId 商品ID
     */
    void recordProductView(String productId);
}
