package com.example.tomatomall.service;

import java.util.List;

/**
 * 延迟队列服务接口
 * 
 * 【Redis 数据结构选型】ZSet
 * Key: delay:queue:{queueName}
 * Member: 任务数据(如订单ID)
 * Score: 执行时间戳(毫秒)
 * 
 * 【延迟队列实现原理】
 * 1. 添加任务时，score = 当前时间 + 延迟时间
 * 2. 消费时，获取 score <= 当前时间的任务
 * 3. 获取到的任务就是"到期"的任务
 * 
 * 【商城典型应用场景】
 * 1. 订单超时取消:下单30分钟后未支付，自动取消
 * 2. 优惠券过期提醒:过期前天发送提醒
 * 3. 评价催促:收货天后发送评价提醒
 * 4. 库存释放:锁定库存超时后释放
 * 
 * 【为什么用 ZSet 而不是其他方案】
 * 
 * 方案1: 定时扫描数据库
 * - 缺点:性能差，数据量大时扫描慢
 * 
 * 方案2: JDK DelayQueue
 * - 缺点:内存队列，重启丢失，不支持分布式
 * 
 * 方案3: RabbitMQ 死信队列
 * - 缺点:不支持任意延迟时间，需要额外中间件
 * 
 * 方案4: Redis ZSet(本方案)
 * - 优点:支持任意延迟、持久化、高性能、分布式
 * 
 * 【面试深度解析�?
 * Q: ZSet 延迟队列的时间精度?
 * A: 取决于轮询间隔，通常 100ms~1s
 * 
 * Q: 如何保证任务不被重复消费�?
 * A: 获取任务后立即删除(ZREM)，或使�?Lua 脚本原子操作
 * 
 * Q: 大量任务同时到期怎么办?
 * A: 分批获取(LIMIT)，多线程消�?
 */
public interface DelayQueueService {
    
    // 订单超时队列名称
    String ORDER_TIMEOUT_QUEUE = "order:timeout";
    
    /**
     * 添加延迟任务
     * @param queueName 队列名称
     * @param taskData 任务数据
     * @param delayMs 延迟时间(毫秒)
     */
    void addDelayTask(String queueName, String taskData, long delayMs);
    
    /**
     * 添加订单超时任务
     * @param orderId 订单ID
     * @param timeoutMinutes 超时时间(分钟)
     */
    void addOrderTimeoutTask(Integer orderId, int timeoutMinutes);
    
    /**
     * 获取到期的任务(不删除)
     * @param queueName 队列名称
     * @param maxCount 最大获取数�?
     * @return 到期的任务列�?
     */
    List<String> getExpiredTasks(String queueName, int maxCount);
    
    /**
     * 获取并删除到期的任务(原子操作)
     * @param queueName 队列名称
     * @param maxCount 最大获取数�?
     * @return 到期的任务列�?
     */
    List<String> pollExpiredTasks(String queueName, int maxCount);
    
    /**
     * 移除任务
     * @param queueName 队列名称
     * @param taskData 任务数据
     */
    void removeTask(String queueName, String taskData);
    
    /**
     * 移除订单超时任务(支付成功后调用�?
     * @param orderId 订单ID
     */
    void removeOrderTimeoutTask(Integer orderId);
    
    /**
     * 获取队列长度
     * @param queueName 队列名称
     * @return 队列中的任务数量
     */
    Long getQueueSize(String queueName);
    
    /**
     * 获取任务剩余延迟时间
     * @param queueName 队列名称
     * @param taskData 任务数据
     * @return 剩余时间(毫秒)，null表示任务不存�?
     */
    Long getTaskRemainingTime(String queueName, String taskData);
}
