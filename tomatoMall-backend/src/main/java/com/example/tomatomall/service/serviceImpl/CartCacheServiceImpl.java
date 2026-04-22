package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.CartItem;
import com.example.tomatomall.repository.CartItemRepository;
import com.example.tomatomall.service.CartCacheService;
import com.example.tomatomall.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Redis 购物车服务实�?
 * 
 * 【Redis 数据结构】Hash
 * Key: cart:user:{userId}
 * Field: productId (商品ID)
 * Value: quantity (数量)
 * 
 * 【Hash 在购物车场景的优势�?
 * 1. 数据组织清晰:一个用户对应一�?Hash，内含多个商�?
 * 2. 原子递增:HINCRBY 原子操作，并发安�?
 * 3. 部分读取:HGET 获取单个商品，HGETALL 获取全部
 * 4. 高效删除:HDEL 删除单个商品，DEL 清空购物�?
 * 
 * 【数据结构示例�?
 * cart:user:1 = {
 *   "100": 2,    // 商品100，数�?
 *   "101": 1,    // 商品101，数�?
 *   "102": 3     // 商品102，数�?
 * }
 * 
 * 【面试深度解析�?
 * Q: 为什么不�?String 存储 JSON�?
 * A: 
 * 1. 修改数量需要读�?修改-写入，非原子操作
 * 2. 高并发下可能丢失更新
 * 3. 每次修改都要序列�?反序列化整个购物�?
 * 
 * Q: Hash 的缺点?
 * A:
 * 1. 无法对字段设置单独的过期时间
 * 2. 字段值只能是字符串，复杂对象需要序列化
 */
@Service
public class CartCacheServiceImpl implements CartCacheService {
    
    // 购物车缓�?Key 前缀
    private static final String CART_CACHE_PREFIX = "cart:user:";
    // 缓存时间�?天)
    private static final long CACHE_TTL_DAYS = 7;
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private CartItemRepository cartItemRepository;
    
    /**
     * 添加商品到购物车
     * 
     * 【实现说明�?
     * 使用 HSET 设置商品数量，如果商品已存在会覆�?
     * 如果需要累加，应该�?incrQuantity
     */
    @Override
    public void addToCart(Integer userId, Integer productId, Integer quantity) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        redisService.hSet(cacheKey, productId.toString(), quantity);
        
        // 设置/更新过期时间
        redisService.expire(cacheKey, CACHE_TTL_DAYS, TimeUnit.DAYS);
    }
    
    /**
     * 更新购物车商品数�?
     */
    @Override
    public void updateQuantity(Integer userId, Integer productId, Integer quantity) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        
        if (quantity <= 0) {
            // 数量�?或负数，删除商品
            removeFromCart(userId, productId);
        } else {
            redisService.hSet(cacheKey, productId.toString(), quantity);
        }
    }
    
    /**
     * 增加商品数量(原子操作)
     * 
     * 【核心方法�?
     * 使用 HINCRBY 原子递增
     * 即使高并发，也能保证数量正确
     * 
     * 【场景示例�?
     * 用户快速点�?添加到购物车"按钮，每次点�?delta=1
     * HINCRBY 保证每次点击都能正确累加
     */
    @Override
    public Long incrQuantity(Integer userId, Integer productId, Long delta) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        Long newQuantity = redisService.hIncr(cacheKey, productId.toString(), delta);
        
        // 如果数量变为0或负数，删除该商�?
        if (newQuantity != null && newQuantity <= 0) {
            removeFromCart(userId, productId);
            return 0L;
        }
        
        // 更新过期时间
        redisService.expire(cacheKey, CACHE_TTL_DAYS, TimeUnit.DAYS);
        
        return newQuantity;
    }
    
    /**
     * 从购物车移除商品
     * 
     * 【Redis 命令】HDEL
     */
    @Override
    public void removeFromCart(Integer userId, Integer productId) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        redisService.hDelete(cacheKey, productId.toString());
    }
    
    /**
     * 获取购物车所有商�?
     * 
     * 【Redis 命令】HGETALL
     * 一次请求获取全部数据，减少网络往�?
     */
    @Override
    public Map<Integer, Integer> getCart(Integer userId) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        
        // 检查缓存是否存�?
        if (!redisService.hasKey(cacheKey)) {
            syncCartFromDB(userId);
        }
        
        Map<Object, Object> rawMap = redisService.hGetAll(cacheKey);
        Map<Integer, Integer> result = new HashMap<>();
        
        if (rawMap != null) {
            for (Map.Entry<Object, Object> entry : rawMap.entrySet()) {
                try {
                    Integer productId = Integer.parseInt(entry.getKey().toString());
                    Integer quantity = Integer.parseInt(entry.getValue().toString());
                    result.put(productId, quantity);
                } catch (NumberFormatException e) {
                    // 跳过无效数据
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取购物车商品数量(种类数)
     * 
     * 【Redis 命令】HLEN
     */
    @Override
    public Long getCartSize(Integer userId) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        return redisService.hSize(cacheKey);
    }
    
    /**
     * 清空购物�?
     * 
     * 【Redis 命令】DEL
     */
    @Override
    public void clearCart(Integer userId) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        redisService.delete(cacheKey);
    }
    
    /**
     * 判断商品是否在购物车�?
     * 
     * 【Redis 命令】HEXISTS
     */
    @Override
    public Boolean isInCart(Integer userId, Integer productId) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        return redisService.hHasKey(cacheKey, productId.toString());
    }
    
    /**
     * 获取购物车中某商品的数量
     * 
     * 【Redis 命令】HGET
     */
    @Override
    public Integer getProductQuantity(Integer userId, Integer productId) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        Object quantity = redisService.hGet(cacheKey, productId.toString());
        
        if (quantity == null) {
            return 0;
        }
        
        return Integer.parseInt(quantity.toString());
    }
    
    /**
     * 同步数据库购物车�?Redis
     * 
     * 【数据同步策略�?
     * �?MySQL 加载用户购物车，批量写入 Redis Hash
     */
    @Override
    public void syncCartFromDB(Integer userId) {
        String cacheKey = CART_CACHE_PREFIX + userId;
        
        // 从数据库加载购物�?
        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);
        
        // 清空旧缓�?
        redisService.delete(cacheKey);
        
        // 写入新数�?
        if (cartItems != null && !cartItems.isEmpty()) {
            Map<String, Object> cartMap = new HashMap<>();
            for (CartItem item : cartItems) {
                cartMap.put(item.getProductId().toString(), item.getQuantity());
            }
            redisService.hSetAll(cacheKey, cartMap);
            
            // 设置过期时间
            redisService.expire(cacheKey, CACHE_TTL_DAYS + new Random().nextInt(2), TimeUnit.DAYS);
        }
    }
}
