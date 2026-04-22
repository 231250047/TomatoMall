package com.example.tomatomall.configure;

import com.example.tomatomall.po.Order;
import com.example.tomatomall.repository.OrderRepository;
import com.example.tomatomall.service.DelayQueueService;
import com.example.tomatomall.service.StockCacheService;
import com.example.tomatomall.service.serviceImpl.OrderServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单超时处理定时任务
 * 
 * 【功能说明】
 * 定时轮询 Redis 延迟队列，处理超时未支付的订单
 * 
 * 【处理流程】
 * 1. 从延迟队列获取到期的订单任务
 * 2. 检查订单状态，如果仍为 PENDING(待支付)
 * 3. 取消订单，释放库�?
 * 4. 更新订单状态为 TIMEOUT
 * 
 * 【Redis 在订单超时中的作用�?
 * - 使用 ZSet 实现延迟队列
 * - 避免数据库轮询，性能更好
 * - 支持任意延迟时间
 * - 持久化保证任务不丢失
 * 
 * 【面试亮点�?
 * - 理解订单超时的业务重要性(库存占用、用户体验)
 * - 掌握 Redis 延迟队列的实现原�?
 * - 了解定时任务与延迟队列的配合
 */
@Component
@EnableScheduling
public class OrderTimeoutTask {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderTimeoutTask.class);
    
    @Autowired
    private DelayQueueService delayQueueService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderServiceImpl orderService;
    
    @Autowired
    private StockCacheService stockCacheService;
    
    /**
     * 处理超时订单
     * 
     * 【定时规则�?
     * �?10 秒执行一�?
     * 可根据业务调整轮询频�?
     * 
     * 【执行流程�?
     * 1. 从延迟队列获取到期任务(最�?00个)
     * 2. 解析订单ID
     * 3. 检查订单状�?
     * 4. 如果是待支付状态，则取消订�?
     */
    @Scheduled(fixedRate = 10000) // �?0秒执行一�?
    public void processTimeoutOrders() {
        try {
            // 1. 获取到期的订单任务(原子获取并删除)
            List<String> expiredTasks = delayQueueService.pollExpiredTasks(
                DelayQueueService.ORDER_TIMEOUT_QUEUE, 
                100
            );
            
            if (expiredTasks.isEmpty()) {
                return;
            }
            
            logger.info("发现 {%d} 个超时订单任务", expiredTasks.size());
            
            // 2. 逐个处理
            for (String taskData : expiredTasks) {
                processTimeoutOrder(taskData);
            }
            
        } catch (Exception e) {
            logger.error("处理超时订单异常", e);
        }
    }
    
    /**
     * 处理单个超时订单
     * 
     * @param taskData 任务数据，格式为 "order:{orderId}"
     */
    private void processTimeoutOrder(String taskData) {
        try {
            // 解析订单ID
            if (!taskData.startsWith("order:")) {
                logger.warn("无效的任务数量 {}", taskData);
                return;
            }
            
            String orderIdStr = taskData.substring(6); // 去掉 "order:" 前缀
            Integer orderId = Integer.parseInt(orderIdStr);
            
            // 查询订单
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                logger.warn("订单不存在 {}", orderId);
                return;
            }
            
            // 检查订单状�?
            if (order.getStatus() == Order.OrderStatus.PENDING) {
                // 订单仍为待支付状态，执行取消
                logger.info("取消超时订单: {}", orderId);
                
                // 更新订单状态为超时
                order.setStatus(Order.OrderStatus.TIMEOUT);
                orderRepository.save(order);
                
                // 释放锁定的库�?
                orderService.releaseLockedStock(orderIdStr);
                
                logger.info("订单 {} 已超时取消，库存已释放", orderId);
            } else {
                // 订单已被处理(支付成功或已取消)
                logger.info("订单 {} 状态为 {}，无需处理", orderId, order.getStatus());
            }
            
        } catch (Exception e) {
            logger.error("处理超时订单失败: {}", taskData, e);
            // 处理失败，可以考虑重新入队
        }
    }
}
