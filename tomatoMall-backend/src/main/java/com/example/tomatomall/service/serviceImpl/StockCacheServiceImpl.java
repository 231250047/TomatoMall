package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Stockpile;
import com.example.tomatomall.repository.StockpileRepository;
import com.example.tomatomall.service.RedisService;
import com.example.tomatomall.service.StockCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存缓存服务实现
 * 
 * 【Redis 数据结构】String
 * Key: stock:product:{productId}
 * Value: 库存数量
 * 
 * 【核心原理：Redis 原子操作防超卖】
 * 
 * 传统方案的问题：
 * ```
 * // 伪代码：非原子操作，有并发问题
 * stock = getStock(productId);        // 读取库存
 * if (stock >= quantity) {            // 判断库存
 *     stock = stock - quantity;       // 计算新库存
 *     setStock(productId, stock);     // 更新库存
 * }
 * // 问题：读取和更新之间，可能有其他线程修改了库存
 * ```
 * 
 * Redis 原子方案：
 * ```
 * // DECR 是原子操作，不会有并发问题
 * newStock = redisTemplate.decr(key, quantity);
 * if (newStock < 0) {
 *     // 库存不足，回滚
 *     redisTemplate.incr(key, quantity);
 *     throw new Exception("库存不足");
 * }
 * ```
 * 
 * 【面试深度解析】
 * 1. Redis 单线程模型保证命令串行执行
 * 2. DECR 命令在服务端原子完成"读取-计算-写入"三步
 * 3. 即使高并发，每个 DECR 都能得到正确的结果
 * 4. 通过返回值判断是否超卖，超卖则回滚
 */
@Service
public class StockCacheServiceImpl implements StockCacheService {
    
    // 库存缓存 Key 前缀
    private static final String STOCK_CACHE_PREFIX = "stock:product:";
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private StockpileRepository stockpileRepository;
    
    /**
     * 初始化库存到 Redis
     * 
     * 【使用场景】
     * 1. 系统启动时预热库存
     * 2. 新商品上架时初始化
     * 3. 库存同步时重置
     */
    @Override
    public void initStock(Integer productId, Integer stock) {
        String cacheKey = STOCK_CACHE_PREFIX + productId;
        redisService.set(cacheKey, stock);
    }
    
    /**
     * 扣减库存（原子操作）
     * 
     * 【核心实现】
     * 使用 DECRBY 原子递减库存：
     * 1. 如果 key 不存在，先初始化为 0 再递减（可能导致负数）
     * 2. 如果返回值 < 0，说明库存不足，需要回滚
     * 3. 回滚也是原子操作，保证数据一致性
     * 
     * 【高并发场景分析】
     * 假设库存为 1，两个请求同时扣减：
     * - 请求A: DECR -> 返回 0 -> 扣减成功
     * - 请求B: DECR -> 返回 -1 -> 库存不足，回滚
     * 
     * Redis 单线程保证 DECR 串行执行，不会出现两个请求都成功的情况
     */
    @Override
    public Long decrStock(Integer productId, Integer quantity) {
        String cacheKey = STOCK_CACHE_PREFIX + productId;
        
        // 检查缓存是否存在，不存在则从数据库同步
        if (!redisService.hasKey(cacheKey)) {
            syncStock(productId);
        }
        
        // 原子递减
        Long newStock = redisService.decr(cacheKey, quantity);
        
        // 如果库存为负，说明超卖，需要回滚
        if (newStock < 0) {
            // 回滚库存
            redisService.incr(cacheKey, quantity);
            return -1L; // 返回 -1 表示库存不足
        }
        
        return newStock;
    }
    
    /**
     * 恢复库存（原子操作）
     * 
     * 【使用场景】
     * 1. 下单失败回滚
     * 2. 订单取消
     * 3. 支付超时
     * 4. 退货退款
     */
    @Override
    public Long incrStock(Integer productId, Integer quantity) {
        String cacheKey = STOCK_CACHE_PREFIX + productId;
        
        // 检查缓存是否存在
        if (!redisService.hasKey(cacheKey)) {
            syncStock(productId);
        }
        
        // 原子递增
        return redisService.incr(cacheKey, quantity);
    }
    
    /**
     * 获取当前库存
     */
    @Override
    public Long getStock(Integer productId) {
        String cacheKey = STOCK_CACHE_PREFIX + productId;
        
        // 检查缓存是否存在
        if (!redisService.hasKey(cacheKey)) {
            syncStock(productId);
        }
        
        Object stock = redisService.get(cacheKey);
        if (stock == null) {
            return 0L;
        }
        return Long.parseLong(stock.toString());
    }
    
    /**
     * 同步单个商品库存到 Redis
     * 
     * 【数据一致性】
     * 从数据库读取可用库存（总库存 - 冻结库存）
     */
    @Override
    public void syncStock(Integer productId) {
        String cacheKey = STOCK_CACHE_PREFIX + productId;
        
        Stockpile stockpile = stockpileRepository.findByProductId(productId);
        if (stockpile != null) {
            // 可用库存 = 总库存 - 冻结库存
            int availableStock = stockpile.getAmount() - stockpile.getFrozen();
            redisService.set(cacheKey, Math.max(0, availableStock));
        }
    }
    
    /**
     * 同步所有商品库存到 Redis
     * 
     * 【使用场景】
     * 系统启动时预热缓存
     */
    @Override
    public void syncAllStock() {
        List<Stockpile> stockpiles = stockpileRepository.findAll();
        for (Stockpile stockpile : stockpiles) {
            String cacheKey = STOCK_CACHE_PREFIX + stockpile.getProduct().getId();
            int availableStock = stockpile.getAmount() - stockpile.getFrozen();
            redisService.set(cacheKey, Math.max(0, availableStock));
        }
    }
    
    /**
     * 删除库存缓存
     */
    @Override
    public void deleteStockCache(Integer productId) {
        String cacheKey = STOCK_CACHE_PREFIX + productId;
        redisService.delete(cacheKey);
    }
}
