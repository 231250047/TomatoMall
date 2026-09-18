package com.example.tomatomall.service;

import com.example.tomatomall.vo.CartItemVO;
import java.util.List;
import java.util.Map;

/**
 * Redis 购物车服务接�?
 * 
 * 【Redis 数据结构选型】Hash
 * Key: cart:user:{userId}
 * Field: productId
 * Value: 商品数量
 * 
 * 【为什么选择 Hash�?
 * 1. 用户购物车包含多个商品，Hash 可以存储多个字段
 * 2. 可以单独操作某个商品(增减数量、删除)
 * 3. HINCRBY 支持原子递增，方便数量修�?
 * 4. 一�?HGETALL 获取全部购物车，减少网络开销
 * 
 * 【Hash vs String 对比�?
 * String 方案:cart:user:1:product:100 = 2
 * - 需要多次请求获取所有商�?
 * - 删除需要遍历所�?key
 * 
 * Hash 方案:cart:user:1 -> {100: 2, 101: 1}
 * - 一次请求获取所有商�?
 * - 操作更高�?
 * 
 * 【面试要点�?
 * - Hash 的字段操作特�?
 * - HINCRBY 原子操作
 * - 数据结构的合理选型
 */
public interface CartCacheService {
    
    /**
     * 添加商品到购物车
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 数量
     */
    void addToCart(Integer userId, Integer productId, Integer quantity);
    
    /**
     * 更新购物车商品数�?
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 新数�?
     */
    void updateQuantity(Integer userId, Integer productId, Integer quantity);
    
    /**
     * 增加商品数量(原子操作)
     * @param userId 用户ID
     * @param productId 商品ID
     * @param delta 增量(可为负数)
     * @return 更新后的数量
     */
    Long incrQuantity(Integer userId, Integer productId, Long delta);
    
    /**
     * 从购物车移除商品
     * @param userId 用户ID
     * @param productId 商品ID
     */
    void removeFromCart(Integer userId, Integer productId);
    
    /**
     * 获取购物车所有商�?
     * @param userId 用户ID
     * @return Map<商品ID, 数量>
     */
    Map<Integer, Integer> getCart(Integer userId);
    
    /**
     * 获取购物车商品数�?
     * @param userId 用户ID
     * @return 商品种类�?
     */
    Long getCartSize(Integer userId);
    
    /**
     * 清空购物�?
     * @param userId 用户ID
     */
    void clearCart(Integer userId);
    
    /**
     * 判断商品是否在购物车�?
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 是否存在
     */
    Boolean isInCart(Integer userId, Integer productId);
    
    /**
     * 获取购物车中某商品的数量
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 数量
     */
    Integer getProductQuantity(Integer userId, Integer productId);
    
    /**
     * 同步数据库购物车�?Redis
     * @param userId 用户ID
     */
    void syncCartFromDB(Integer userId);
}
