package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.DelayQueueService;
import com.example.tomatomall.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 延迟队列服务实现
 * 
 * 【Redis 数据结构】ZSet
 * Key: delay:queue:{queueName}
 * Member: 任务数据
 * Score: 执行时间戳(毫秒�?
 * 
 * 【核心原理�?
 * ```
 * // 添加延迟任务�?0分钟后执�?
 * ZADD delay:queue:order:timeout <当前时间+30分钟> "order:123"
 * 
 * // 获取到期任务:score <= 当前时间
 * ZRANGEBYSCORE delay:queue:order:timeout 0 <当前时间> LIMIT 0 100
 * 
 * // 删除已处理的任务
 * ZREM delay:queue:order:timeout "order:123"
 * ```
 * 
 * 【订单超时处理流程�?
 * 1. 用户下单 -> 添加延迟任务�?0分钟后)
 * 2. 定时任务轮询 -> 获取到期订单
 * 3. 检查订单状�?-> 未支付则取消，释放库�?
 * 4. 用户支付成功 -> 移除延迟任务
 * 
 * 【高可用设计�?
 * 1. 任务持久化:Redis 持久化保证任务不丢失
 * 2. 分布式消费:多实例可以同时消费(需配合分布式锁�?
 * 3. 重试机制:消费失败可以重新入�?
 */
@Service
public class DelayQueueServiceImpl implements DelayQueueService {
    
    // 延迟队列 Key 前缀
    private static final String DELAY_QUEUE_PREFIX = "delay:queue:";
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 添加延迟任务
     * 
     * 【核心方法�?
     * score = 当前时间�?+ 延迟时间
     * 任务�?score 排序，最早执行的排在前面
     */
    @Override
    public void addDelayTask(String queueName, String taskData, long delayMs) {
        String queueKey = DELAY_QUEUE_PREFIX + queueName;
        // 计算执行时间
        long executeTime = System.currentTimeMillis() + delayMs;
        // 添加�?ZSet
        redisService.zAdd(queueKey, taskData, executeTime);
    }
    
    /**
     * 添加订单超时任务
     * 
     * 【业务场景�?
     * 下单后调用，设置30分钟超时
     */
    @Override
    public void addOrderTimeoutTask(Integer orderId, int timeoutMinutes) {
        String taskData = "order:" + orderId;
        long delayMs = timeoutMinutes * 60 * 1000L;
        addDelayTask(ORDER_TIMEOUT_QUEUE, taskData, delayMs);
    }
    
    /**
     * 获取到期的任务(不删除)
     * 
     * 【Redis 命令】ZRANGEBYSCORE
     * 获取 score �?[0, 当前时间] 范围内的元素
     * 即:所有应该执行的任务
     */
    @Override
    public List<String> getExpiredTasks(String queueName, int maxCount) {
        String queueKey = DELAY_QUEUE_PREFIX + queueName;
        long now = System.currentTimeMillis();
        
        // 获取所有到期任务(score <= 当前时间�?
        Set<Object> tasks = redisService.zRangeByScore(queueKey, 0, now);
        
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 限制返回数量
        return tasks.stream()
                .map(Object::toString)
                .limit(maxCount)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取并删除到期的任务
     * 
     * 【原子性保证�?
     * 使用 Lua 脚本保证"获取+删除"的原子�?
     * 防止多个消费者重复消费同一任务
     * 
     * 【Lua 脚本说明�?
     * 1. ZRANGEBYSCORE 获取到期任务
     * 2. ZREM 删除这些任务
     * 3. 返回被删除的任务列表
     */
    @Override
    public List<String> pollExpiredTasks(String queueName, int maxCount) {
        String queueKey = DELAY_QUEUE_PREFIX + queueName;
        long now = System.currentTimeMillis();
        
        // Lua 脚本:原子获取并删除到期任务
        String luaScript = 
            "local tasks = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[2]) " +
            "if #tasks > 0 then " +
            "    redis.call('ZREM', KEYS[1], unpack(tasks)) " +
            "end " +
            "return tasks";
        
        DefaultRedisScript<List> script = new DefaultRedisScript<>(luaScript, List.class);
        
        try {
            List result = redisTemplate.execute(
                script, 
                Collections.singletonList(queueKey), 
                String.valueOf(now), 
                String.valueOf(maxCount)
            );
            
            if (result == null || result.isEmpty()) {
                return new ArrayList<>();
            }
            
            return (List<String>) result.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // Lua 执行失败，降级为非原子操�?
            List<String> tasks = getExpiredTasks(queueName, maxCount);
            for (String task : tasks) {
                removeTask(queueName, task);
            }
            return tasks;
        }
    }
    
    /**
     * 移除任务
     * 
     * 【Redis 命令】ZREM
     */
    @Override
    public void removeTask(String queueName, String taskData) {
        String queueKey = DELAY_QUEUE_PREFIX + queueName;
        redisService.zRemove(queueKey, taskData);
    }
    
    /**
     * 移除订单超时任务
     * 
     * 【业务场景�?
     * 用户支付成功后调用，取消超时检�?
     */
    @Override
    public void removeOrderTimeoutTask(Integer orderId) {
        String taskData = "order:" + orderId;
        removeTask(ORDER_TIMEOUT_QUEUE, taskData);
    }
    
    /**
     * 获取队列长度
     * 
     * 【Redis 命令】ZCARD
     */
    @Override
    public Long getQueueSize(String queueName) {
        String queueKey = DELAY_QUEUE_PREFIX + queueName;
        return redisService.zSize(queueKey);
    }
    
    /**
     * 获取任务剩余延迟时间
     * 
     * 【实现说明�?
     * 剩余时间 = score(执行时间)- 当前时间
     * 如果 <= 0，说明已到期
     */
    @Override
    public Long getTaskRemainingTime(String queueName, String taskData) {
        String queueKey = DELAY_QUEUE_PREFIX + queueName;
        Double score = redisService.zScore(queueKey, taskData);
        
        if (score == null) {
            return null; // 任务不存�?
        }
        
        long remaining = score.longValue() - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
