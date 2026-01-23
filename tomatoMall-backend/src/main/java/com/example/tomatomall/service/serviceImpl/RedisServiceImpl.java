package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存服务实现类
 * 
 * 【设计理念】
 * 1. 统一封装 RedisTemplate 操作，提供更友好的 API
 * 2. 处理序列化、空值检查等通用逻辑
 * 3. 为上层业务提供可靠的缓存操作能力
 * 
 * 【Redis 在商城项目中的核心价值】
 * 1. 性能提升：热点数据缓存，减少数据库压力
 * 2. 高并发支持：原子操作保证数据一致性
 * 3. 业务扩展：排行榜、延迟队列等高级功能
 */
@Service
public class RedisServiceImpl implements RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== String 类型操作实现 ====================

    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    @Override
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    @Override
    public Long delete(List<String> keys) {
        return redisTemplate.delete(keys);
    }

    @Override
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    @Override
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    @Override
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 原子递增操作
     * 
     * 【Redis 特性】
     * INCR/INCRBY 是原子操作，在高并发场景下保证线程安全
     * 适用于：计数器、库存扣减、限流等场景
     */
    @Override
    public Long incr(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    @Override
    public Long decr(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    /**
     * SETNX 实现
     * 
     * 【Redis 特性】
     * SET key value NX EX timeout 是原子操作
     * 常用于：分布式锁、防止缓存击穿（互斥锁重建缓存）
     */
    @Override
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    // ==================== Hash 类型操作实现 ====================

    @Override
    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    @Override
    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    @Override
    public void hSet(String key, String hashKey, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForHash().put(key, hashKey, value);
        expire(key, timeout, unit);
    }

    @Override
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    @Override
    public void hSetAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    @Override
    public void hSetAll(String key, Map<String, Object> map, long timeout, TimeUnit unit) {
        redisTemplate.opsForHash().putAll(key, map);
        expire(key, timeout, unit);
    }

    @Override
    public Long hDelete(String key, Object... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    @Override
    public Boolean hHasKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    /**
     * Hash 字段原子递增
     * 
     * 【应用场景】
     * 1. 购物车商品数量增减：HINCRBY cart:userId productId delta
     * 2. 商品浏览次数统计：HINCRBY product:1 viewCount 1
     */
    @Override
    public Long hIncr(String key, String hashKey, Long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    @Override
    public Long hSize(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    // ==================== List 类型操作实现 ====================

    @Override
    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    @Override
    public Long lSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    @Override
    public Object lIndex(String key, long index) {
        return redisTemplate.opsForList().index(key, index);
    }

    @Override
    public Long lRightPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    @Override
    public Long lRightPushAll(String key, Object... values) {
        return redisTemplate.opsForList().rightPushAll(key, values);
    }

    @Override
    public Object lLeftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    @Override
    public Object lRightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 阻塞式弹出
     * 
     * 【Redis 特性】
     * BLPOP/BRPOP 支持阻塞等待，直到有元素可弹出或超时
     * 适用于：简单消息队列、任务队列
     */
    @Override
    public Object lLeftPop(String key, long timeout, TimeUnit unit) {
        return redisTemplate.opsForList().leftPop(key, timeout, unit);
    }

    @Override
    public Long lRemove(String key, long count, Object value) {
        return redisTemplate.opsForList().remove(key, count, value);
    }

    // ==================== Set 类型操作实现 ====================

    /**
     * 添加元素到 Set
     * 
     * 【Redis 特性】
     * Set 自动去重，添加已存在的元素不会报错，返回实际添加的数量
     * 适用于：收藏夹、标签、共同好友等场景
     */
    @Override
    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    @Override
    public Long sRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    @Override
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 判断元素是否存在于 Set 中
     * 
     * 【Redis 特性】
     * SISMEMBER 时间复杂度 O(1)，非常高效
     * 适用于：判断用户是否已收藏、是否已点赞等
     */
    @Override
    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    @Override
    public Long sSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    // ==================== ZSet 类型操作实现 ====================

    /**
     * 添加元素到 ZSet
     * 
     * 【Redis 特性】
     * ZSet 按 score 自动排序，相同元素更新 score
     * 适用于：排行榜、热搜、延迟队列
     */
    @Override
    public Boolean zAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    @Override
    public Long zRemove(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    /**
     * 增加元素的分数
     * 
     * 【Redis 特性】
     * ZINCRBY 是原子操作，适合高并发场景下的分数累加
     * 适用于：商品热度增加、搜索词频率统计
     */
    @Override
    public Double zIncrScore(String key, Object value, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, value, delta);
    }

    @Override
    public Long zRank(String key, Object value) {
        return redisTemplate.opsForZSet().rank(key, value);
    }

    @Override
    public Long zReverseRank(String key, Object value) {
        return redisTemplate.opsForZSet().reverseRank(key, value);
    }

    /**
     * 获取排行榜（从大到小）
     * 
     * 【应用场景】
     * 获取热搜 Top 10：zReverseRange("search:hot", 0, 9)
     */
    @Override
    public Set<Object> zReverseRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    @Override
    public Map<Object, Double> zReverseRangeWithScores(String key, long start, long end) {
        Set<ZSetOperations.TypedTuple<Object>> tuples = 
            redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        Map<Object, Double> result = new LinkedHashMap<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                result.put(tuple.getValue(), tuple.getScore());
            }
        }
        return result;
    }

    @Override
    public Long zCount(String key, double min, double max) {
        return redisTemplate.opsForZSet().count(key, min, max);
    }

    @Override
    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    @Override
    public Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    @Override
    public Long zRemoveRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().removeRange(key, start, end);
    }

    /**
     * 按分数范围移除元素
     * 
     * 【应用场景】
     * 延迟队列：移除所有到期任务（score <= 当前时间戳）
     */
    @Override
    public Long zRemoveRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().removeRangeByScore(key, min, max);
    }

    /**
     * 按分数范围获取元素
     * 
     * 【应用场景】
     * 延迟队列：获取所有到期任务
     */
    @Override
    public Set<Object> zRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }
}
