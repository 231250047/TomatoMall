package com.example.tomatomall.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存服务接口
 * 
 * 【Redis 数据结构与业务场景对应�?
 * 1. String - 用户会话、计数器、分布式�?
 * 2. Hash - 商品详情缓存、购物车
 * 3. List - 消息队列、广告轮�?
 * 4. Set - 收藏列表、标签、去�?
 * 5. ZSet - 排行榜、延迟队�?
 * 
 * 【面试亮点�?
 * - 熟练掌握 Redis 五种基础数据结构及其应用场景
 * - 理解 Redis 原子操作在高并发场景的应�?
 * - 掌握缓存设计模式(Cache Aside、Write Through等)
 */
public interface RedisService {
    
    // ==================== String 类型操作 ====================
    
    /**
     * 设置缓存
     * 【应用场景】用户会话存储、商品详情缓�?
     */
    void set(String key, Object value);
    
    /**
     * 设置缓存并指定过期时�?
     * 【应用场景】验证码、临时token、限时活动数�?
     * 【Redis特性】TTL(Time To Live)自动过期机�?
     */
    void set(String key, Object value, long timeout, TimeUnit unit);
    
    /**
     * 获取缓存
     */
    Object get(String key);
    
    /**
     * 删除缓存
     */
    Boolean delete(String key);
    
    /**
     * 批量删除缓存
     * 【应用场景】清理某类缓存，如清理所有商品缓�?
     */
    Long delete(List<String> keys);
    
    /**
     * 设置过期时间
     */
    Boolean expire(String key, long timeout, TimeUnit unit);
    
    /**
     * 获取过期时间
     */
    Long getExpire(String key, TimeUnit unit);
    
    /**
     * 判断 key 是否存在
     */
    Boolean hasKey(String key);
    
    /**
     * 原子递增
     * 【应用场景】PV统计、库存扣减、计数器
     * 【Redis特性】INCR 命令是原子操作，线程安全
     */
    Long incr(String key, long delta);
    
    /**
     * 原子递减
     * 【应用场景】库存扣减、限流计�?
     */
    Long decr(String key, long delta);
    
    /**
     * SETNX:只�?key 不存在时才设�?
     * 【应用场景】分布式锁、防止缓存击�?
     * 【Redis特性】原子操作，实现互斥
     */
    Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit);
    
    // ==================== Hash 类型操作 ====================
    
    /**
     * 获取 Hash 中指定字段的�?
     * 【应用场景】获取商品某个属性、获取购物车某个商品
     */
    Object hGet(String key, String hashKey);
    
    /**
     * 设置 Hash 中指定字段的�?
     * 【应用场景】更新商品某个属性、更新购物车商品数量
     */
    void hSet(String key, String hashKey, Object value);
    
    /**
     * 设置 Hash 中指定字段的值并设置过期时间
     */
    void hSet(String key, String hashKey, Object value, long timeout, TimeUnit unit);
    
    /**
     * 获取 Hash 中所有字段和�?
     * 【应用场景】获取完整商品信息、获取用户购物车
     * 【Redis特性】HGETALL 一次获取所有字段，减少网络开销
     */
    Map<Object, Object> hGetAll(String key);
    
    /**
     * 批量设置 Hash 字段
     * 【应用场景】缓存商品详情(多个字段�?
     * 【Redis特性】HMSET 批量操作，原子�?
     */
    void hSetAll(String key, Map<String, Object> map);
    
    /**
     * 批量设置 Hash 字段并设置过期时�?
     */
    void hSetAll(String key, Map<String, Object> map, long timeout, TimeUnit unit);
    
    /**
     * 删除 Hash 中指定字�?
     * 【应用场景】删除购物车中某个商�?
     */
    Long hDelete(String key, Object... hashKeys);
    
    /**
     * 判断 Hash 中是否存在指定字�?
     */
    Boolean hHasKey(String key, String hashKey);
    
    /**
     * Hash 字段原子递增
     * 【应用场景】购物车商品数量增减、商品浏览次数统�?
     * 【Redis特性】HINCRBY 原子操作
     */
    Long hIncr(String key, String hashKey, Long delta);
    
    /**
     * 获取 Hash 大小
     */
    Long hSize(String key);
    
    // ==================== List 类型操作 ====================
    
    /**
     * 获取 List 指定范围的元�?
     * 【应用场景】获取广告列表、获取消息列�?
     * 【Redis特性】支持负数索引，-1 表示最后一个元�?
     */
    List<Object> lRange(String key, long start, long end);
    
    /**
     * 获取 List 长度
     */
    Long lSize(String key);
    
    /**
     * 根据索引获取 List 元素
     */
    Object lIndex(String key, long index);
    
    /**
     * 从右侧插入元�?
     * 【应用场景】添加新广告、添加新消息
     */
    Long lRightPush(String key, Object value);
    
    /**
     * 批量从右侧插入元�?
     * 【应用场景】批量添加广�?
     */
    Long lRightPushAll(String key, Object... values);
    
    /**
     * 从左侧弹出元�?
     * 【应用场景】消息队列消费、任务处�?
     * 【Redis特性】LPOP 原子操作
     */
    Object lLeftPop(String key);
    
    /**
     * 从右侧弹出元�?
     */
    Object lRightPop(String key);
    
    /**
     * 阻塞式从左侧弹出
     * 【应用场景】消息队列、延迟任�?
     * 【Redis特性】BLPOP 阻塞操作，实现消息等�?
     */
    Object lLeftPop(String key, long timeout, TimeUnit unit);
    
    /**
     * 删除 List 中指定值的元素
     */
    Long lRemove(String key, long count, Object value);
    
    // ==================== Set 类型操作 ====================
    
    /**
     * 添加元素�?Set
     * 【应用场景】用户收藏商品、商品标�?
     * 【Redis特性】自动去�?
     */
    Long sAdd(String key, Object... values);
    
    /**
     * �?Set 移除元素
     * 【应用场景】取消收�?
     */
    Long sRemove(String key, Object... values);
    
    /**
     * 获取 Set 所有元�?
     * 【应用场景】获取用户所有收�?
     */
    Set<Object> sMembers(String key);
    
    /**
     * 判断元素是否�?Set �?
     * 【应用场景】判断用户是否已收藏某商�?
     * 【Redis特性】SISMEMBER O(1) 时间复杂�?
     */
    Boolean sIsMember(String key, Object value);
    
    /**
     * 获取 Set 大小
     */
    Long sSize(String key);
    
    // ==================== ZSet 类型操作 ====================
    
    /**
     * 添加元素�?ZSet 并指定分�?
     * 【应用场景】商品热度排行、搜索热�?
     * 【Redis特性】自动按分数排序
     */
    Boolean zAdd(String key, Object value, double score);
    
    /**
     * �?ZSet 移除元素
     */
    Long zRemove(String key, Object... values);
    
    /**
     * 增加元素的分�?
     * 【应用场景】商品被搜索/浏览时增加热�?
     * 【Redis特性】ZINCRBY 原子操作
     */
    Double zIncrScore(String key, Object value, double delta);
    
    /**
     * 获取元素排名(从小到大)
     */
    Long zRank(String key, Object value);
    
    /**
     * 获取元素排名(从大到小)
     * 【应用场景】获取商品热度排�?
     */
    Long zReverseRank(String key, Object value);
    
    /**
     * 获取指定排名范围的元素(从大到小�?
     * 【应用场景】获取热搜榜 Top N
     */
    Set<Object> zReverseRange(String key, long start, long end);
    
    /**
     * 获取指定排名范围的元素和分数(从大到小)
     */
    Map<Object, Double> zReverseRangeWithScores(String key, long start, long end);
    
    /**
     * 获取指定分数范围的元素数�?
     */
    Long zCount(String key, double min, double max);
    
    /**
     * 获取 ZSet 大小
     */
    Long zSize(String key);
    
    /**
     * 获取元素分数
     */
    Double zScore(String key, Object value);
    
    /**
     * 移除指定排名范围的元�?
     * 【应用场景】清理过期热搜数�?
     */
    Long zRemoveRange(String key, long start, long end);
    
    /**
     * 移除指定分数范围的元�?
     * 【应用场景】延迟队列处理到期任�?
     */
    Long zRemoveRangeByScore(String key, double min, double max);
    
    /**
     * 获取指定分数范围的元�?
     * 【应用场景】延迟队列获取到期任�?
     */
    Set<Object> zRangeByScore(String key, double min, double max);
}
