package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.ProductCacheService;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.service.RedisService;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.SpecificationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 商品缓存服务实现
 * 
 * 【Redis 数据结构】Hash
 * Key: product:detail:{productId}
 * Field-Value: 商品各属�?
 * 
 * 【缓存策略】Cache Aside Pattern(旁路缓存)
 * - 读取:先读缓存，未命中再�?DB，然后写缓存
 * - 写入:先更新 DB，再删除缓存
 * 
 * 【缓存三大问题解决方案�?
 * 1. 缓存穿透:缓存空值(NULL_OBJECT 标记�?
 * 2. 缓存击穿:互斥锁重建缓存(SETNX�?
 * 3. 缓存雪崩:随机过期时�?
 * 
 * 【场景实现�?
 * - 完整的缓存设计方�?
 * - 缓存一致性保�?
 * - 高并发场景下的缓存保�?
 */
@Service
public class ProductCacheServiceImpl implements ProductCacheService {
    
    // 缓存 Key 前缀
    private static final String PRODUCT_CACHE_PREFIX = "product:detail:";
    // 商品热度 Key(ZSet�?
    private static final String PRODUCT_HOT_KEY = "product:hot";
    // 缓存锁前缀
    private static final String CACHE_LOCK_PREFIX = "lock:product:";
    // 空值标记，防止缓存穿�?
    private static final String NULL_CACHE_VALUE = "NULL_OBJECT";
    
    // 基础缓存时间�?0分钟�?
    private static final long CACHE_TTL_MINUTES = 30;
    // 空值缓存时间(5分钟�?
    private static final long NULL_CACHE_TTL_MINUTES = 5;
    // 锁超时时间(10秒)
    private static final long LOCK_TTL_SECONDS = 10;
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 从缓存获取商品详�?
     * 
     * 【实现说明�?
     * 使用 Hash 存储商品，可以高效获�?更新单个字段
     */
    @Override
    public ProductVO getProductFromCache(String productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        
        // 检查缓存是否存�?
        if (!redisService.hasKey(cacheKey)) {
            return null;
        }
        
        // 获取所有字�?
        Map<Object, Object> cacheMap = redisService.hGetAll(cacheKey);
        if (cacheMap == null || cacheMap.isEmpty()) {
            return null;
        }
        
        // 检查是否为空值标记(防止缓存穿透)
        if (NULL_CACHE_VALUE.equals(cacheMap.get("_null"))) {
            // 返回特殊标记，表示数据库中不存在
            return null;
        }
        
        return mapToProductVO(cacheMap);
    }
    
    /**
     * 将商品详情存入缓�?
     * 
     * 【缓存雪崩防护�?
     * 使用随机过期时间，避免大量缓存同时失�?
     */
    @Override
    public void setProductToCache(ProductVO productVO) {
        if (productVO == null || productVO.getId() == null) {
            return;
        }
        
        String cacheKey = PRODUCT_CACHE_PREFIX + productVO.getId();
        Map<String, Object> cacheMap = productVOToMap(productVO);
        
        // 随机过期时间:基础时间 + 0~10分钟随机值(防止缓存雪崩�?
        long randomTTL = CACHE_TTL_MINUTES + new Random().nextInt(10);
        
        redisService.hSetAll(cacheKey, cacheMap, randomTTL, TimeUnit.MINUTES);
    }
    
    /**
     * 删除商品缓存
     * 
     * 【Cache Aside Pattern�?
     * 更新数据库后，删除缓存而不是更新缓�?
     * 原因:避免并发更新导致的数据不一�?
     */
    @Override
    public void deleteProductCache(String productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        redisService.delete(cacheKey);
    }
    
    /**
     * 获取商品详情(优先缓存)
     * 
     * 【完整的缓存策略实现�?
     * 1. 缓存穿透防护:缓存空�?
     * 2. 缓存击穿防护:互斥锁重建
     * 3. 缓存雪崩防护:随机过期时�?
     */
    @Override
    public ProductVO getProductWithCache(String productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        
        // 1. 尝试从缓存获�?
        if (redisService.hasKey(cacheKey)) {
            Map<Object, Object> cacheMap = redisService.hGetAll(cacheKey);
            
            // 检查空值标记(缓存穿透防护)
            if (cacheMap != null && NULL_CACHE_VALUE.equals(cacheMap.get("_null"))) {
                return null; // 数据库中不存在该商品
            }
            
            if (cacheMap != null && !cacheMap.isEmpty()) {
                return mapToProductVO(cacheMap);
            }
        }
        
        // 2. 缓存未命中，尝试获取锁重建缓存(缓存击穿防护�?
        String lockKey = CACHE_LOCK_PREFIX + productId;
        boolean locked = redisService.setIfAbsent(lockKey, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        
        try {
            if (locked) {
                // 获取锁成功，从数据库加载
                return loadAndCacheProduct(productId);
            } else {
                // 获取锁失败，短暂等待后重试从缓存获取
                Thread.sleep(50);
                if (redisService.hasKey(cacheKey)) {
                    Map<Object, Object> cacheMap = redisService.hGetAll(cacheKey);
                    if (cacheMap != null && !cacheMap.isEmpty() && 
                        !NULL_CACHE_VALUE.equals(cacheMap.get("_null"))) {
                        return mapToProductVO(cacheMap);
                    }
                }
                // 仍然未命中，降级直接查数据库
                return loadFromDatabase(productId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loadFromDatabase(productId);
        } finally {
            // 释放�?
            if (locked) {
                redisService.delete(lockKey);
            }
        }
    }
    
    /**
     * 记录商品浏览，用于热度统�?
     * 
     * 【Redis 数据结构】ZSet
     * 使用 ZINCRBY 原子递增分数，实现浏览次数统�?
     */
    @Override
    public void recordProductView(String productId) {
        // 使用 ZSet 记录商品热度，每次浏览增�?1 �?
        redisService.zIncrScore(PRODUCT_HOT_KEY, productId, 1);
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 从数据库加载并缓存商�?
     */
    private ProductVO loadAndCacheProduct(String productId) {
        ProductVO productVO = loadFromDatabase(productId);
        
        if (productVO != null) {
            setProductToCache(productVO);
        } else {
            // 缓存空值，防止缓存穿�?
            cacheNullValue(productId);
        }
        
        return productVO;
    }
    
    /**
     * 从数据库加载商品
     */
    private ProductVO loadFromDatabase(String productId) {
        try {
            return productService.getProduct(productId);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 缓存空�?
     * 
     * 【缓存穿透防护�?
     * 当数据库中不存在该数据时，缓存一个空值标�?
     * 设置较短的过期时间，避免占用过多内存
     */
    private void cacheNullValue(String productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        Map<String, Object> nullMap = new HashMap<>();
        nullMap.put("_null", NULL_CACHE_VALUE);
        redisService.hSetAll(cacheKey, nullMap, NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }
    
    /**
     * ProductVO 转换�?Map(用�?Hash 存储�?
     */
    private Map<String, Object> productVOToMap(ProductVO vo) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", vo.getId() != null ? vo.getId() : "");
        map.put("title", vo.getTitle() != null ? vo.getTitle() : "");
        map.put("price", vo.getPrice() != null ? vo.getPrice().toString() : "0");
        map.put("rate", vo.getRate() != null ? vo.getRate().toString() : "0");
        map.put("description", vo.getDescription() != null ? vo.getDescription() : "");
        map.put("cover", vo.getCover() != null ? vo.getCover() : "");
        map.put("detail", vo.getDetail() != null ? vo.getDetail() : "");
        map.put("tag", vo.getTag() != null ? vo.getTag() : "");
        map.put("sellerId", vo.getSellerId() != null ? vo.getSellerId().toString() : "");
        map.put("sellerName", vo.getSellerName() != null ? vo.getSellerName() : "");
        map.put("sellerAvatar", vo.getSellerAvatar() != null ? vo.getSellerAvatar() : "");
        map.put("status", vo.getStatus() != null ? vo.getStatus() : "");
        map.put("condition", vo.getCondition() != null ? vo.getCondition() : "");
        return map;
    }
    
    /**
     * Map 转换�?ProductVO
     */
    private ProductVO mapToProductVO(Map<Object, Object> map) {
        ProductVO vo = new ProductVO();
        
        String id = getStringValue(map, "id");
        if (!id.isEmpty()) {
            vo.setId(id);
        }
        
        vo.setTitle(getStringValue(map, "title"));
        
        String price = getStringValue(map, "price");
        if (!price.isEmpty()) {
            vo.setPrice(new BigDecimal(price));
        }
        
        String rate = getStringValue(map, "rate");
        if (!rate.isEmpty()) {
            vo.setRate(Double.parseDouble(rate));
        }
        
        vo.setDescription(getStringValue(map, "description"));
        vo.setCover(getStringValue(map, "cover"));
        vo.setDetail(getStringValue(map, "detail"));
        vo.setTag(getStringValue(map, "tag"));
        
        String sellerId = getStringValue(map, "sellerId");
        if (!sellerId.isEmpty()) {
            vo.setSellerId(Integer.parseInt(sellerId));
        }
        
        vo.setSellerName(getStringValue(map, "sellerName"));
        vo.setSellerAvatar(getStringValue(map, "sellerAvatar"));
        vo.setStatus(getStringValue(map, "status"));
        vo.setCondition(getStringValue(map, "condition"));
        
        return vo;
    }
    
    private String getStringValue(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }
}
