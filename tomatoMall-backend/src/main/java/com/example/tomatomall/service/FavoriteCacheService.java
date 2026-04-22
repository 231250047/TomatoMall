package com.example.tomatomall.service;

import java.util.Set;

/**
 * 收藏缓存服务接口
 * 
 * 【Redis 数据结构选型】Set
 * 选择 Set 的原因:
 * 1. 收藏记录天然具有唯一性(同一用户不能重复收藏同一商品�?
 * 2. 需要快速判断某商品是否已被收藏(SISMEMBER O(1)�?
 * 3. 不需要排序(如需要按时间排序，考虑 ZSet�?
 * 
 * 【缓�?Key 设计�?
 * - 用户收藏集合:favorite:user:{userId}
 * 
 * 【面试要点�?
 * - Set 的去重特�?
 * - SISMEMBER 的高效�?
 * - Set �?ZSet 的选择依据
 */
public interface FavoriteCacheService {
    
    /**
     * 添加收藏到缓�?
     * @param userId 用户ID
     * @param productId 商品ID
     */
    void addFavoriteToCache(Integer userId, Integer productId);
    
    /**
     * 从缓存移除收�?
     * @param userId 用户ID
     * @param productId 商品ID
     */
    void removeFavoriteFromCache(Integer userId, Integer productId);
    
    /**
     * 判断商品是否已被收藏(优先缓存)
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 是否已收�?
     */
    Boolean isFavorited(Integer userId, Integer productId);
    
    /**
     * 获取用户收藏的商品ID集合
     * @param userId 用户ID
     * @return 商品ID集合
     */
    Set<Integer> getUserFavorites(Integer userId);
    
    /**
     * 获取用户收藏数量
     * @param userId 用户ID
     * @return 收藏数量
     */
    Long getFavoriteCount(Integer userId);
    
    /**
     * 同步用户收藏到缓�?
     * @param userId 用户ID
     */
    void syncUserFavorites(Integer userId);
    
    /**
     * 删除用户收藏缓存
     * @param userId 用户ID
     */
    void deleteUserFavoriteCache(Integer userId);
}
