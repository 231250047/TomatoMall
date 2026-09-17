package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Favorite;
import com.example.tomatomall.repository.FavoriteRepository;
import com.example.tomatomall.service.FavoriteCacheService;
import com.example.tomatomall.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 收藏缓存服务实现
 * 
 * 【Redis 数据结构】Set
 * Key: favorite:user:{userId}
 * Value: 商品ID集合
 * 
 * 【Set 特性应用�?
 * 1. 自动去重:同一商品只会存储一�?
 * 2. O(1) 判断:SISMEMBER 快速判断是否已收藏
 * 3. 高效统计:SCARD 快速获取收藏数�?
 * 
 * 【面试亮点�?
 * - Set 的去重和快速判断特�?
 * - 缓存与数据库的一致性保�?
 */
@Service
public class FavoriteCacheServiceImpl implements FavoriteCacheService {
    
    // 收藏缓存 Key 前缀
    private static final String FAVORITE_CACHE_PREFIX = "favorite:user:";
    // 缓存时间�?小时�?
    private static final long CACHE_TTL_MINUTES = 120;
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private FavoriteRepository favoriteRepository;
    
    /**
     * 添加收藏到缓�?
     * 
     * 【Set 特性�?
     * SADD 操作:如果元素已存在，不会重复添�?
     */
    @Override
    public void addFavoriteToCache(Integer userId, Integer productId) {
        String cacheKey = FAVORITE_CACHE_PREFIX + userId;
        redisService.sAdd(cacheKey, productId.toString());
        
        // 设置/更新过期时间
        redisService.expire(cacheKey, CACHE_TTL_MINUTES + new Random().nextInt(30), TimeUnit.MINUTES);
    }
    
    /**
     * 从缓存移除收�?
     * 
     * 【Set 特性�?
     * SREM 操作:移除指定元素，如果不存在则忽略
     */
    @Override
    public void removeFavoriteFromCache(Integer userId, Integer productId) {
        String cacheKey = FAVORITE_CACHE_PREFIX + userId;
        redisService.sRemove(cacheKey, productId.toString());
    }
    
    /**
     * 判断商品是否已被收藏(优先缓存)
     * 
     * 【Set 特性�?
     * SISMEMBER 操作:O(1) 时间复杂度判断元素是否存�?
     * 这是 Set 的核心优势之一
     */
    @Override
    public Boolean isFavorited(Integer userId, Integer productId) {
        String cacheKey = FAVORITE_CACHE_PREFIX + userId;
        
        // 如果缓存存在，直接从缓存判断
        if (redisService.hasKey(cacheKey)) {
            return redisService.sIsMember(cacheKey, productId.toString());
        }
        
        // 缓存不存在，从数据库加载并缓�?
        syncUserFavorites(userId);
        return redisService.sIsMember(cacheKey, productId.toString());
    }
    
    /**
     * 获取用户收藏的商品ID集合
     * 
     * 【Set 特性�?
     * SMEMBERS 操作:获取集合所有成�?
     */
    @Override
    public Set<Integer> getUserFavorites(Integer userId) {
        String cacheKey = FAVORITE_CACHE_PREFIX + userId;
        
        // 如果缓存不存在，先同�?
        if (!redisService.hasKey(cacheKey)) {
            syncUserFavorites(userId);
        }
        
        Set<Object> members = redisService.sMembers(cacheKey);
        if (members == null) {
            return new HashSet<>();
        }
        
        return members.stream()
                .map(obj -> Integer.parseInt(obj.toString()))
                .collect(Collectors.toSet());
    }
    
    /**
     * 获取用户收藏数量
     * 
     * 【Set 特性�?
     * SCARD 操作:O(1) 时间复杂度获取集合大�?
     */
    @Override
    public Long getFavoriteCount(Integer userId) {
        String cacheKey = FAVORITE_CACHE_PREFIX + userId;
        
        // 如果缓存不存在，先同�?
        if (!redisService.hasKey(cacheKey)) {
            syncUserFavorites(userId);
        }
        
        Long size = redisService.sSize(cacheKey);
        return size != null ? size : 0L;
    }
    
    /**
     * 同步用户收藏到缓�?
     * 
     * 【数据同步策略�?
     * 从数据库加载用户所有收藏，批量写入 Redis Set
     */
    @Override
    public void syncUserFavorites(Integer userId) {
        String cacheKey = FAVORITE_CACHE_PREFIX + userId;
        
        // 从数据库加载
        List<Favorite> favorites = favoriteRepository.findByAccountIdOrderByCreateTimeDesc(userId);
        
        // 删除旧缓�?
        redisService.delete(cacheKey);
        
        // 如果有收藏，写入缓存
        if (favorites != null && !favorites.isEmpty()) {
            Object[] productIds = favorites.stream()
                    .map(f -> f.getProductId().toString())
                    .toArray();
            redisService.sAdd(cacheKey, productIds);
            
            // 设置过期时间(加随机值防止雪崩)
            redisService.expire(cacheKey, CACHE_TTL_MINUTES + new Random().nextInt(30), TimeUnit.MINUTES);
        }
    }
    
    /**
     * 删除用户收藏缓存
     */
    @Override
    public void deleteUserFavoriteCache(Integer userId) {
        String cacheKey = FAVORITE_CACHE_PREFIX + userId;
        redisService.delete(cacheKey);
    }
}
