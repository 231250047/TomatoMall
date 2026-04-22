package com.example.tomatomall.service;

/**
 * 库存缓存服务接口
 * 
 * 【Redis 在库存管理中的核心作用�?
 * 使用 Redis String 的原子操作(DECR/INCR)解决高并发下的超卖问题
 * 
 * 【传统方案的问题�?
 * 1. 数据库乐观锁:高并发下大量更新失败，性能�?
 * 2. 数据库悲观锁:阻塞其他请求，吞吐量低
 * 3. 程序加锁:单机有效，分布式环境无�?
 * 
 * 【Redis 方案优势�?
 * 1. DECR 原子操作:保证并发安�?
 * 2. 内存操作:高性能�?0�? QPS�?
 * 3. 返回值判断:负数表示超卖，可以回�?
 * 
 * 【面试亮点�?
 * - 深入理解高并发场景下的库存问�?
 * - Redis 原子操作的实际应�?
 * - 缓存与数据库的一致性保�?
 */
public interface StockCacheService {
    
    /**
     * 初始化库存到 Redis
     * @param productId 商品ID
     * @param stock 库存数量
     */
    void initStock(Integer productId, Integer stock);
    
    /**
     * 扣减库存(原子操作)
     * 
     * 【核心方法�?
     * 使用 DECR 原子递减，返回扣减后的库�?
     * 如果返回负数，说明库存不足，需要回�?
     * 
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 扣减后的库存，负数表示库存不�?
     */
    Long decrStock(Integer productId, Integer quantity);
    
    /**
     * 恢复库存(原子操作)
     * 
     * 【使用场景�?
     * 1. 扣减失败时回�?
     * 2. 订单取消时恢�?
     * 3. 支付超时时恢�?
     * 
     * @param productId 商品ID
     * @param quantity 恢复数量
     * @return 恢复后的库存
     */
    Long incrStock(Integer productId, Integer quantity);
    
    /**
     * 获取当前库存
     * @param productId 商品ID
     * @return 当前库存
     */
    Long getStock(Integer productId);
    
    /**
     * 同步库存�?Redis
     * @param productId 商品ID
     */
    void syncStock(Integer productId);
    
    /**
     * 同步所有商品库存到 Redis
     */
    void syncAllStock();
    
    /**
     * 删除库存缓存
     * @param productId 商品ID
     */
    void deleteStockCache(Integer productId);
}
