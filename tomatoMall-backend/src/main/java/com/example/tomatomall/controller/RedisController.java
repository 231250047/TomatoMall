package com.example.tomatomall.controller;

import com.example.tomatomall.service.*;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 功能演示 Controller
 * 
 * 【功能说明】
 * 提供 Redis 各种功能的 API 接口，用于:
 * 1. 面试时演示 Redis 功能
 * 2. 测试 Redis 服务是否正常
 * 3. 查看缓存数据
 * 
 * 【接口分类】
 * 1. 商品缓存:/api/redis/product/*
 * 2. 热搜榜:/api/redis/hot/*
 * 3. 统计:/api/redis/stats/*
 * 4. 库存:/api/redis/stock/*
 */
@RestController
@RequestMapping("/api/redis")
public class RedisController {
    
    @Autowired
    private ProductCacheService productCacheService;
    
    @Autowired
    private HotRankService hotRankService;
    
    @Autowired
    private StatisticsService statisticsService;
    
    @Autowired
    private StockCacheService stockCacheService;
    
    @Autowired
    private CartCacheService cartCacheService;
    
    @Autowired
    private FavoriteCacheService favoriteCacheService;
    
    @Autowired
    private DelayQueueService delayQueueService;
    
    // ==================== 商品缓存 API ====================
    
    /**
     * 获取商品详情(带缓存�?
     * 
     * 【演示要点�?
     * 1. 第一次访问:缓存未命中，从数据库加载
     * 2. 第二次访问:缓存命中，直接返�?
     * 3. 体现 Cache Aside 模式
     */
    @GetMapping("/product/{productId}")
    public Response<ProductVO> getProduct(@PathVariable String productId) {
        ProductVO product = productCacheService.getProductWithCache(productId);
        if (product == null) {
            return Response.buildFailure("商品不存在", "404");
        }
        return Response.buildSuccess(product);
    }
    
    /**
     * 删除商品缓存
     * 
     * 【演示要点�?
     * 商品信息更新后，应删除缓存保证一致�?
     */
    @DeleteMapping("/product/{productId}/cache")
    public Response<String> deleteProductCache(@PathVariable String productId) {
        productCacheService.deleteProductCache(productId);
        return Response.buildSuccess("商品缓存已删除");
    }
    
    // ==================== 热搜�?API ====================
    
    /**
     * 记录搜索热度
     * 
     * 【演示要点�?
     * 使用 ZSet ZINCRBY 原子递增分数
     */
    @PostMapping("/hot/search")
    public Response<String> recordSearch(@RequestParam String keyword) {
        hotRankService.recordSearch(keyword);
        return Response.buildSuccess("搜索记录成功");
    }
    
    /**
     * 获取热搜关键�?Top N
     * 
     * 【演示要点�?
     * 使用 ZSet ZREVRANGE 获取排行�?
     */
    @GetMapping("/hot/keywords")
    public Response<List<String>> getHotKeywords(
            @RequestParam(defaultValue = "10") int topN) {
        List<String> keywords = hotRankService.getHotKeywords(topN);
        return Response.buildSuccess(keywords);
    }
    
    /**
     * 获取热搜关键词及分数
     */
    @GetMapping("/hot/keywords/scores")
    public Response<Map<String, Double>> getHotKeywordsWithScore(
            @RequestParam(defaultValue = "10") int topN) {
        Map<String, Double> keywords = hotRankService.getHotKeywordsWithScore(topN);
        return Response.buildSuccess(keywords);
    }
    
    /**
     * 记录商品浏览热度
     */
    @PostMapping("/hot/product/{productId}")
    public Response<String> recordProductView(@PathVariable Integer productId) {
        hotRankService.recordProductView(productId);
        return Response.buildSuccess("浏览记录成功");
    }
    
    /**
     * 获取热门商品 Top N
     */
    @GetMapping("/hot/products")
    public Response<Map<Integer, Double>> getHotProducts(
            @RequestParam(defaultValue = "10") int topN) {
        Map<Integer, Double> products = hotRankService.getHotProductsWithScore(topN);
        return Response.buildSuccess(products);
    }
    
    // ==================== PV/UV 统计 API ====================
    
    /**
     * 记录页面访问(PV + UV�?
     * 
     * 【演示要点�?
     * PV: String INCR
     * UV: HyperLogLog PFADD
     */
    @PostMapping("/stats/visit")
    public Response<String> recordVisit(
            @RequestParam String pageKey,
            @RequestParam String userId) {
        // 记录 PV
        statisticsService.incrPV(pageKey);
        statisticsService.incrTodayPV(pageKey);
        // 记录 UV
        statisticsService.recordUV(pageKey, userId);
        statisticsService.recordTodayUV(pageKey, userId);
        return Response.buildSuccess("访问记录成功");
    }
    
    /**
     * 获取页面统计数据
     * 
     * 【演示要点�?
     * 返回 PV(精确)�?UV(HyperLogLog 估算，约 0.81% 误差�?
     */
    @GetMapping("/stats/{pageKey}")
    public Response<Map<String, Long>> getPageStats(@PathVariable String pageKey) {
        Map<String, Long> stats = statisticsService.getPageStats(pageKey);
        return Response.buildSuccess(stats);
    }
    
    /**
     * 记录商品访问
     */
    @PostMapping("/stats/product/{productId}")
    public Response<String> recordProductVisit(
            @PathVariable Integer productId,
            @RequestParam String userId) {
        statisticsService.incrProductPV(productId);
        statisticsService.recordProductUV(productId, userId);
        hotRankService.recordProductView(productId);
        return Response.buildSuccess("商品访问记录成功");
    }
    
    /**
     * 获取商品统计数据
     */
    @GetMapping("/stats/product/{productId}")
    public Response<Map<String, Object>> getProductStats(@PathVariable Integer productId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pv", statisticsService.getProductPV(productId));
        stats.put("uv", statisticsService.getProductUV(productId));
        stats.put("hotRank", hotRankService.getProductRank(productId));
        return Response.buildSuccess(stats);
    }
    
    // ==================== 库存 API ====================
    
    /**
     * 获取商品库存
     * 
     * 【演示要点�?
     * �?Redis 获取缓存的库�?
     */
    @GetMapping("/stock/{productId}")
    public Response<Long> getStock(@PathVariable Integer productId) {
        Long stock = stockCacheService.getStock(productId);
        return Response.buildSuccess(stock);
    }
    
    /**
     * 扣减库存(原子操作)
     * 
     * 【演示要点�?
     * 使用 DECRBY 原子扣减，防止超�?
     */
    @PostMapping("/stock/{productId}/decr")
    public Response<Object> decrStock(
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "1") Integer quantity) {
        Long newStock = stockCacheService.decrStock(productId, quantity);
        if (newStock < 0) {
            return Response.buildFailure("库存不足", "400");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("productId", productId);
        result.put("remainingStock", newStock);
        return Response.buildSuccess(result);
    }
    
    /**
     * 恢复库存
     */
    @PostMapping("/stock/{productId}/incr")
    public Response<Long> incrStock(
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "1") Integer quantity) {
        Long newStock = stockCacheService.incrStock(productId, quantity);
        return Response.buildSuccess(newStock);
    }
    
    /**
     * 同步库存�?Redis
     */
    @PostMapping("/stock/sync")
    public Response<String> syncAllStock() {
        stockCacheService.syncAllStock();
        return Response.buildSuccess("库存同步完成");
    }
    
    // ==================== 延迟队列 API ====================
    
    /**
     * 获取延迟队列大小
     */
    @GetMapping("/delay/order/size")
    public Response<Long> getDelayQueueSize() {
        Long size = delayQueueService.getQueueSize(DelayQueueService.ORDER_TIMEOUT_QUEUE);
        return Response.buildSuccess(size);
    }
    
    /**
     * 添加测试延迟任务
     */
    @PostMapping("/delay/test")
    public Response<String> addTestDelayTask(
            @RequestParam String taskData,
            @RequestParam(defaultValue = "60000") long delayMs) {
        delayQueueService.addDelayTask("test", taskData, delayMs);
        return Response.buildSuccess("延迟任务已添加，将在 " + delayMs + "ms 后到");
    }
    
    // ==================== 缓存状�?API ====================
    
    /**
     * 获取 Redis 缓存状态摘�?
     * 
     * 【演示要点�?
     * 一览各类缓存的状�?
     */
    @GetMapping("/status")
    public Response<Map<String, Object>> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 延迟队列状�?
        status.put("orderTimeoutQueueSize", 
            delayQueueService.getQueueSize(DelayQueueService.ORDER_TIMEOUT_QUEUE));
        
        // 热搜数量
        status.put("hotKeywordsCount", hotRankService.getHotKeywords(1000).size());
        status.put("hotProductsCount", hotRankService.getHotProducts(1000).size());
        
        return Response.buildSuccess(status);
    }
}
