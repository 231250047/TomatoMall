package com.example.tomatomall.configure;

import com.example.tomatomall.service.StockCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存预热
 * 
 * 【功能说明】
 * 应用启动时预加载热点数据到 Redis
 * 
 * 【预热内容】
 * 1. 商品库存：避免首次访问时缓存未命中
 * 
 * 【为什么要预热】
 * 1. 避免缓存冷启动：系统重启后，缓存为空，大量请求打到数据库
 * 2. 提升首次访问性能：用户首次访问时，数据已在缓存中
 * 3. 保护数据库：防止重启后的流量洪峰
 * 
 * 【面试要点】
 * - 缓存预热是生产环境的标准做法
 * - 预热应该是异步的，不阻塞应用启动
 * - 大量数据预热时要注意分批加载
 */
@Component
public class RedisCacheWarmer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisCacheWarmer.class);
    
    @Autowired
    private StockCacheService stockCacheService;
    
    /**
     * 应用启动后执行
     * 
     * 【实现说明】
     * 使用 CommandLineRunner 接口，在 Spring 容器初始化完成后执行
     */
    @Override
    public void run(String... args) {
        logger.info("========== 开始 Redis 缓存预热 ==========");
        
        try {
            // 预热商品库存
            warmUpStock();
            
            logger.info("========== Redis 缓存预热完成 ==========");
        } catch (Exception e) {
            // 预热失败不应影响应用启动
            logger.error("Redis 缓存预热失败，将使用懒加载模式", e);
        }
    }
    
    /**
     * 预热商品库存
     * 
     * 【作用】
     * 将所有商品库存加载到 Redis
     * 后续的库存扣减操作可以直接在 Redis 中进行
     */
    private void warmUpStock() {
        logger.info("预热商品库存...");
        try {
            stockCacheService.syncAllStock();
            logger.info("商品库存预热完成");
        } catch (Exception e) {
            logger.warn("商品库存预热失败: {}", e.getMessage());
        }
    }
}
