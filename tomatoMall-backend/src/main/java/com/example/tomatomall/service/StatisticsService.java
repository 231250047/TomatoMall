package com.example.tomatomall.service;

import java.util.Map;

/**
 * PV/UV 统计服务接口
 * 
 * 【基本概念】
 * PV (Page View)：页面浏览量，用户每访问一次页面就计数 +1
 * UV (Unique Visitor)：独立访客数，同一用户多次访问只计数 1
 * 
 * 【Redis 数据结构选型】
 * PV: String + INCR 原子递增
 * UV: HyperLogLog（推荐）或 Set
 * 
 * 【为什么 UV 用 HyperLogLog】
 * 
 * 方案1: Set 存储用户ID
 * - 优点：精确统计
 * - 缺点：内存占用大（1亿用户约 1.6GB）
 * 
 * 方案2: HyperLogLog
 * - 优点：内存极小（固定 12KB），支持大规模统计
 * - 缺点：有 0.81% 误差率
 * 
 * 【HyperLogLog 原理简述】
 * 基于概率算法，通过观察随机数二进制前导零的分布来估算基数
 * 面试时不需要深入细节，重点是：
 * 1. 适合大规模去重计数
 * 2. 固定 12KB 内存
 * 3. 误差约 0.81%
 * 4. 支持合并（PFMERGE）
 * 
 */
public interface StatisticsService {
    
    /**
     * 增加页面 PV
     * @param pageKey 页面标识
     */
    void incrPV(String pageKey);
    
    /**
     * 增加商品 PV
     * @param productId 商品ID
     */
    void incrProductPV(Integer productId);
    
    /**
     * 获取页面 PV
     * @param pageKey 页面标识
     * @return PV 数量
     */
    Long getPV(String pageKey);
    
    /**
     * 获取商品 PV
     * @param productId 商品ID
     * @return PV 数量
     */
    Long getProductPV(Integer productId);
    
    /**
     * 记录页面 UV（使用 HyperLogLog）
     * @param pageKey 页面标识
     * @param userId 用户标识（可以是用户ID或设备ID）
     */
    void recordUV(String pageKey, String userId);
    
    /**
     * 记录商品 UV
     * @param productId 商品ID
     * @param userId 用户标识
     */
    void recordProductUV(Integer productId, String userId);
    
    /**
     * 获取页面 UV（估算值）
     * @param pageKey 页面标识
     * @return UV 数量（可能有 0.81% 误差）
     */
    Long getUV(String pageKey);
    
    /**
     * 获取商品 UV
     * @param productId 商品ID
     * @return UV 数量
     */
    Long getProductUV(Integer productId);
    
    /**
     * 记录今日 PV
     * @param pageKey 页面标识
     */
    void incrTodayPV(String pageKey);
    
    /**
     * 记录今日 UV
     * @param pageKey 页面标识
     * @param userId 用户标识
     */
    void recordTodayUV(String pageKey, String userId);
    
    /**
     * 获取今日 PV
     * @param pageKey 页面标识
     * @return 今日 PV
     */
    Long getTodayPV(String pageKey);
    
    /**
     * 获取今日 UV
     * @param pageKey 页面标识
     * @return 今日 UV
     */
    Long getTodayUV(String pageKey);
    
    /**
     * 获取页面统计摘要（PV + UV）
     * @param pageKey 页面标识
     * @return Map 包含 pv 和 uv
     */
    Map<String, Long> getPageStats(String pageKey);
}
