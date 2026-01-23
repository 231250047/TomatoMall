package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.RedisService;
import com.example.tomatomall.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PV/UV 统计服务实现
 * 
 * 【Redis 数据结构】
 * 
 * PV 统计：String + INCR
 * Key: stats:pv:{pageKey}
 * Value: 访问次数
 * 
 * UV 统计：HyperLogLog
 * Key: stats:uv:{pageKey}
 * Value: HyperLogLog 数据结构
 * 
 * 【PV 统计原理】
 * ```
 * // 每次页面访问
 * INCR stats:pv:homepage
 * INCR stats:pv:product:100
 * 
 * // INCR 是原子操作，高并发下也能保证准确性
 * ```
 * 
 * 【UV 统计原理 - HyperLogLog】
 * ```
 * // 记录用户访问
 * PFADD stats:uv:homepage user:123
 * PFADD stats:uv:homepage user:456
 * PFADD stats:uv:homepage user:123  // 重复添加不会增加计数
 * 
 * // 获取独立访客数
 * PFCOUNT stats:uv:homepage  // 返回 2（去重后）
 * ```
 * 
 * 【HyperLogLog 核心特性】
 * 1. 空间效率：固定 12KB 存储，不随数据量增加
 * 2. 去重能力：自动去重，同一元素多次添加只计数一次
 * 3. 误差率：标准误差 0.81%
 * 4. 合并能力：PFMERGE 可以合并多个 HyperLogLog
 * 
 * 【按天统计设计】
 * Key: stats:pv:{pageKey}:20240115
 * 使用日期后缀实现按天统计
 * 设置过期时间自动清理历史数据
 * 
 * 【面试深度解析】
 * Q: 为什么 HyperLogLog 只占 12KB？
 * A: 内部使用 16384 个 6bit 的桶（12KB = 16384 * 6 / 8）
 * 
 * Q: 如何实现精确 UV 统计？
 * A: 使用 Set（SADD + SCARD），但会占用更多内存
 * 
 * Q: HyperLogLog 适合什么场景？
 * A: 大规模去重计数，允许小误差，如 UV、DAU 统计
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {
    
    // PV Key 前缀
    private static final String PV_PREFIX = "stats:pv:";
    // UV Key 前缀
    private static final String UV_PREFIX = "stats:uv:";
    // 商品统计前缀
    private static final String PRODUCT_PREFIX = "product:";
    // 日期格式
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 每日统计数据保留天数
    private static final int DAILY_STATS_TTL_DAYS = 30;
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // ==================== PV 统计 ====================
    
    /**
     * 增加页面 PV
     * 
     * 【Redis 命令】INCR
     * 原子递增，高并发安全
     */
    @Override
    public void incrPV(String pageKey) {
        String key = PV_PREFIX + pageKey;
        redisService.incr(key, 1);
    }
    
    /**
     * 增加商品 PV
     */
    @Override
    public void incrProductPV(Integer productId) {
        String key = PV_PREFIX + PRODUCT_PREFIX + productId;
        redisService.incr(key, 1);
    }
    
    /**
     * 获取页面 PV
     */
    @Override
    public Long getPV(String pageKey) {
        String key = PV_PREFIX + pageKey;
        Object value = redisService.get(key);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }
    
    /**
     * 获取商品 PV
     */
    @Override
    public Long getProductPV(Integer productId) {
        String key = PV_PREFIX + PRODUCT_PREFIX + productId;
        Object value = redisService.get(key);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }
    
    // ==================== UV 统计 (HyperLogLog) ====================
    
    /**
     * 记录页面 UV
     * 
     * 【Redis 命令】PFADD
     * 向 HyperLogLog 添加元素
     * 如果元素已存在，不会增加计数
     */
    @Override
    public void recordUV(String pageKey, String userId) {
        String key = UV_PREFIX + pageKey;
        HyperLogLogOperations<String, Object> ops = redisTemplate.opsForHyperLogLog();
        ops.add(key, userId);
    }
    
    /**
     * 记录商品 UV
     */
    @Override
    public void recordProductUV(Integer productId, String userId) {
        String key = UV_PREFIX + PRODUCT_PREFIX + productId;
        HyperLogLogOperations<String, Object> ops = redisTemplate.opsForHyperLogLog();
        ops.add(key, userId);
    }
    
    /**
     * 获取页面 UV
     * 
     * 【Redis 命令】PFCOUNT
     * 返回 HyperLogLog 的基数估算值
     * 误差率约 0.81%
     */
    @Override
    public Long getUV(String pageKey) {
        String key = UV_PREFIX + pageKey;
        HyperLogLogOperations<String, Object> ops = redisTemplate.opsForHyperLogLog();
        return ops.size(key);
    }
    
    /**
     * 获取商品 UV
     */
    @Override
    public Long getProductUV(Integer productId) {
        String key = UV_PREFIX + PRODUCT_PREFIX + productId;
        HyperLogLogOperations<String, Object> ops = redisTemplate.opsForHyperLogLog();
        return ops.size(key);
    }
    
    // ==================== 每日统计 ====================
    
    /**
     * 增加今日 PV
     * 
     * 【设计说明】
     * Key 包含日期，自动按天分组
     * 设置 30 天过期，自动清理历史数据
     */
    @Override
    public void incrTodayPV(String pageKey) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = PV_PREFIX + pageKey + ":" + today;
        
        redisService.incr(key, 1);
        // 设置过期时间（首次设置）
        if (redisService.getExpire(key, TimeUnit.SECONDS) == -1) {
            redisService.expire(key, DAILY_STATS_TTL_DAYS, TimeUnit.DAYS);
        }
    }
    
    /**
     * 记录今日 UV
     */
    @Override
    public void recordTodayUV(String pageKey, String userId) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = UV_PREFIX + pageKey + ":" + today;
        
        HyperLogLogOperations<String, Object> ops = redisTemplate.opsForHyperLogLog();
        ops.add(key, userId);
        
        // 设置过期时间
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl == -1) {
            redisTemplate.expire(key, DAILY_STATS_TTL_DAYS, TimeUnit.DAYS);
        }
    }
    
    /**
     * 获取今日 PV
     */
    @Override
    public Long getTodayPV(String pageKey) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = PV_PREFIX + pageKey + ":" + today;
        Object value = redisService.get(key);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }
    
    /**
     * 获取今日 UV
     */
    @Override
    public Long getTodayUV(String pageKey) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = UV_PREFIX + pageKey + ":" + today;
        HyperLogLogOperations<String, Object> ops = redisTemplate.opsForHyperLogLog();
        return ops.size(key);
    }
    
    /**
     * 获取页面统计摘要
     */
    @Override
    public Map<String, Long> getPageStats(String pageKey) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("pv", getPV(pageKey));
        stats.put("uv", getUV(pageKey));
        stats.put("todayPV", getTodayPV(pageKey));
        stats.put("todayUV", getTodayUV(pageKey));
        return stats;
    }
}
