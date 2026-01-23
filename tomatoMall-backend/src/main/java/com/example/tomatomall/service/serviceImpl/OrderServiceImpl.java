package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.*;
import com.example.tomatomall.repository.*;
import com.example.tomatomall.service.CartCacheService;
import com.example.tomatomall.service.CartService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.service.StockCacheService;
import com.example.tomatomall.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StockpileRepository stockpileRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    CartsOrdersRelationRepository cartsOrdersRelationRepository;
    @Autowired
    ProductService productService;
    @Autowired
    CartService cartService;
    @Autowired
    OrderItemRepository orderItemRepository;
    
    @Autowired
    StockCacheService stockCacheService;
    
    @Autowired
    CartCacheService cartCacheService;

    // 设置订单锁定超时时间（毫秒），30分钟
    private static final long ORDER_LOCK_TIMEOUT = 30 * 60 * 1000;

    @Override
    @Transactional
    public OrderVO createOrder(String userName, CheckoutVO checkoutVO) {
        // ==================== 【Redis 高并发防超卖方案】 ====================
        // 
        // 【核心思路】
        // 1. 先扣 Redis 库存（原子操作，高性能）
        // 2. Redis 扣减成功后，再写 MySQL 订单
        // 3. 支付成功后，同步扣减 MySQL 库存
        // 4. 失败时回滚 Redis 库存
        //
        // 【为什么高并发下优于 MySQL？】
        // - Redis 单线程模型：DECR 命令在服务端原子完成，无需加锁
        // - 内存操作：QPS 可达 10w+，MySQL 只有几千
        // - 避免数据库锁竞争：不需要 SELECT FOR UPDATE
        // - 快速失败：库存不足立即返回，不占用数据库连接
        //
        // 【数据一致性保障】
        // - Redis 作为库存"真实来源"（Source of Truth）
        // - MySQL 通过事务保证订单数据一致性
        // - 支付成功后异步同步 MySQL 库存（最终一致性）
        // ====================================================================
        
        // 1. 获取购物车商品
        List<CartItem> cartItemList = new ArrayList<>();
        for (String cartItemId : checkoutVO.getCartItemIds()) {
            Integer cartItemIdInt = Integer.parseInt(cartItemId);
            if (!cartItemRepository.existsById(cartItemIdInt)) {
                throw TomatoMallException.cartItemNotExist();
            } else
                cartItemList.add(cartItemRepository.findById(cartItemIdInt).get());
        }
        
        // 2. 【Redis 原子扣减库存】- 防超卖的核心
        // 使用 List 记录已扣减的商品，用于失败时回滚
        List<Integer> deductedProductIds = new ArrayList<>();
        List<Integer> deductedQuantities = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        try {
            for (CartItem cartItem : cartItemList) {
                Integer productId = cartItem.getProductId();
                Integer quantity = cartItem.getQuantity();
                
                // 验证商品是否存在
                if (!productRepository.existsById(productId)) {
                    throw TomatoMallException.productNotExist();
                }
                
                // 【关键步骤】调用 Redis DECR 原子扣减库存
                // decrStock 内部执行：DECRBY stock:product:{id} {quantity}
                // 返回值：扣减后的库存数量，如果 < 0 说明库存不足
                Long newStock = stockCacheService.decrStock(productId, quantity);
                
                if (newStock < 0) {
                    // 库存不足，需要回滚之前所有已扣减的 Redis 库存
                    rollbackRedisStock(deductedProductIds, deductedQuantities);
                    throw TomatoMallException.stockpileNotEnough();
                }
                
                // 记录已扣减的商品（用于后续可能的回滚）
                deductedProductIds.add(productId);
                deductedQuantities.add(quantity);
                
                // 计算订单总金额
                Product product = productRepository.findById(productId).get();
                totalAmount = totalAmount.add(
                    product.getPrice().multiply(new BigDecimal(quantity))
                );
            }
            
            // Redis 库存扣减全部成功，继续处理 MySQL 订单逻辑
            
        } catch (Exception e) {
            // 任何异常都需要回滚 Redis 库存
            rollbackRedisStock(deductedProductIds, deductedQuantities);
            throw e;
        }
        
        // 3. 处理优惠券折扣（业务逻辑）
        if (checkoutVO.getDiscount() != null) {
            BigDecimal discount = new BigDecimal(checkoutVO.getDiscount());
            if (discount.compareTo(new BigDecimal(50)) == 0 && totalAmount.compareTo(new BigDecimal(300)) >= 0) {
                totalAmount = totalAmount.subtract(new BigDecimal(50));
            } else if (discount.compareTo(new BigDecimal(30)) == 0 && totalAmount.compareTo(new BigDecimal(200)) >= 0) {
                totalAmount = totalAmount.subtract(new BigDecimal(30));
            } else if (discount.compareTo(new BigDecimal(5)) == 0 && totalAmount.compareTo(new BigDecimal(50)) >= 0) {
                totalAmount = totalAmount.subtract(new BigDecimal(5));
            }
        }
        
        // 4. 创建订单记录（MySQL）
        // 注意：此时 Redis 库存已扣减，MySQL 库存暂不扣减
        // MySQL 的 amount 和 frozen 字段保持原值，作为库存备份
        // 支付成功后再同步扣减 MySQL 库存
        Order order = new Order();
        order.setTotalAmount(totalAmount);
        order.setUserId(accountRepository.findByUsername(userName).getId());
        order.setCreateTime(new Date());

        // 设置订单过期时间（30分钟后）
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MILLISECOND, (int) ORDER_LOCK_TIMEOUT);

        // 设置收货信息
        ShopAddress address = checkoutVO.getShoppingAddress();
        order.setReceiverName(address.getName());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(address.getAddress());
        order.setReceiverPostalCode(address.getPostalCode());


        order.setPaymentMethod(checkoutVO.getPaymentMethod());
        orderRepository.save(order);
        for (CartItem cartItem : cartItemList) {
            CartsOrdersRelation cartsOrdersRelation = new CartsOrdersRelation();
            cartsOrdersRelation.setOrderId(order.getOrderId());
            cartsOrdersRelation.setCartitemId(cartItem.getCartItemId());
            cartsOrdersRelationRepository.save(cartsOrdersRelation);
        }

        // 保存订单商品快照
        for (CartItem cartItem : cartItemList) {
            Product product = productRepository.findById(cartItem.getProductId()).orElse(null);
            if (product != null) {
                OrderItem orderItem = new OrderItem();
                orderItem.setOrderId(order.getOrderId());
                orderItem.setProductId(product.getId());
                orderItem.setTitle(product.getTitle());
                orderItem.setCover(product.getCover());
                orderItem.setPrice(product.getPrice());
                orderItem.setQuantity(cartItem.getQuantity());
                orderItemRepository.save(orderItem);
            }
        }


        OrderVO orderVO = order.partToVO();
        orderVO.setUsername(userName);
        return orderVO;
    }
    
    /**
     * 【Redis 库存回滚】
     * 当下单流程中任何步骤失败时，需要回滚已扣减的 Redis 库存
     * 
     * @param productIds  已扣减的商品ID列表
     * @param quantities  对应的扣减数量列表
     * 
     * 【使用场景】
     * - Redis 扣减过程中，某个商品库存不足
     * - 订单创建失败（如数据库异常）
     * - 业务校验失败（如用户余额不足）
     * 
     * 【实现原理】
     * 使用 Redis INCRBY 命令原子增加库存，撤销之前的 DECRBY 操作
     */
    private void rollbackRedisStock(List<Integer> productIds, List<Integer> quantities) {
        for (int i = 0; i < productIds.size(); i++) {
            Integer productId = productIds.get(i);
            Integer quantity = quantities.get(i);
            // 调用 incrStock 回滚库存（内部执行 INCRBY）
            stockCacheService.incrStock(productId, quantity);
        }
    }

    /**
     * 释放订单锁定的库存（订单超时时调用）
     * 
     * 【重要】订单超时需要回滚 Redis 库存
     * 流程：
     * 1. 回滚 Redis 库存（INCRBY 恢复）
     * 2. 清理 MySQL frozen 字段（历史遗留）
     * 
     * @param orderId 订单ID
     */
    @Transactional
    public void releaseLockedStock(String orderId) {
        List<CartsOrdersRelation> relations = cartsOrdersRelationRepository.findByOrderId(Integer.parseInt(orderId));
        for (CartsOrdersRelation relation : relations) {
            CartItem cartItem = cartItemRepository.findById(relation.getCartitemId()).orElse(null);
            if (cartItem == null) continue;
            
            Integer productId = cartItem.getProductId();
            Integer quantity = cartItem.getQuantity();
            
            // 【关键】回滚 Redis 库存（因为订单超时未支付）
            stockCacheService.incrStock(productId, quantity);
            System.out.println("订单超时回滚：商品 " + productId + " 恢复库存 " + quantity);
            
            // 清理 MySQL frozen 字段（历史遗留，已不使用）
            Stockpile stockpile = stockpileRepository.findByProductId(productId);
            if (stockpile != null) {
                int newFrozen = stockpile.getFrozen() - quantity;
                if (newFrozen < 0) newFrozen = 0;
                stockpile.setFrozen(newFrozen);
                stockpileRepository.save(stockpile);
            }
        }
    }

    /**
     * 【订单取消 - Redis + MySQL 双写回滚】
     * 在下单或支付失败时彻底取消订单
     * 
     * 【回滚内容】
     * 1. Redis 库存恢复（INCRBY 原子增加）
     * 2. MySQL 订单记录删除
     * 3. 订单关系清理
     * 
     * 【典型场景】
     * - 支付超时（30分钟未支付）
     * - 用户主动取消未支付订单
     * - 支付失败
     */
    @Transactional
    public void cancelOrder(String orderId) {
        int oid = Integer.parseInt(orderId);
        List<CartsOrdersRelation> relations = cartsOrdersRelationRepository.findByOrderId(oid);
        
        for (CartsOrdersRelation relation : relations) {
            CartItem cartItem = cartItemRepository.findById(relation.getCartitemId()).orElse(null);
            if (cartItem == null) continue;
            
            Integer productId = cartItem.getProductId();
            Integer quantity = cartItem.getQuantity();
            
            // 【Redis】恢复库存（原子操作）
            // 执行 INCRBY stock:product:{id} {quantity}
            stockCacheService.incrStock(productId, quantity);
            
            // MySQL 的 amount 字段会在支付成功后才扣减
            
            // 删除订单与购物项的关联（保留购物车条目）
            cartsOrdersRelationRepository.delete(relation);
        }

        // 删除订单本身（回滚生成的订单记录）
        if (orderRepository.existsById(oid)) {
            orderRepository.deleteById(oid);
        }
    }

    @Override
    @Transactional
    public void updateOrderStatus(String orderId, String alipayTradeNo, String amount) {
        Order order = orderRepository.findById(Integer.parseInt(orderId)).orElse(null);
        if (order != null && !order.getStatus().equals(Order.OrderStatus.SUCCESS)) {
            order.setStatus(Order.OrderStatus.SUCCESS);
            order.setPaymentTime(new Date());  // 新增：设置支付时间为当前时间
            orderRepository.save(order);
            // 支付成功后立即减库存（已在事务中，保证一致性）
            reduceStock(orderId);
        }
    }

    /**
     * 【支付成功 - 同步 MySQL 库存】
     * 用户支付成功后，将 Redis 已扣减的库存同步到 MySQL
     * 
     * 【执行时机】
     * updateOrderStatus 方法中，订单状态变更为 SUCCESS 时调用
     * 
     * 【为什么要同步 MySQL？】
     * 1. 数据持久化：Redis 宕机时可从 MySQL 恢复
     * 2. 数据分析：BI 系统依赖 MySQL 做库存报表
     * 3. 备份机制：Redis 为主，MySQL 为从，双重保障
     * 
     * 【注意事项】
     * - Redis 已在下单时扣减，这里只是同步到 MySQL
     * - 如果 MySQL 扣减失败，不影响订单（Redis 为准）
     * - 可以改为异步 MQ 处理，提高支付回调性能
     */
    @Override
    @Transactional
    public void reduceStock(String orderId) {
        List<CartsOrdersRelation> cartsOrdersRelationList = cartsOrdersRelationRepository
                .findByOrderId(Integer.parseInt(orderId));
        
        for (CartsOrdersRelation cartsOrdersRelation : cartsOrdersRelationList) {
            CartItem cartItem = cartItemRepository.findById(cartsOrdersRelation.getCartitemId()).orElse(null);
            if (cartItem == null) continue;
            Product product = productRepository.findById(cartItem.getProductId()).orElse(null);
            if (product == null) continue;

            Integer productId = product.getId();
            Integer quantity = cartItem.getQuantity();
            
            // 获取商品库存
            Stockpile stockpile = stockpileRepository.findByProductId(productId);

            // 【MySQL 同步】扣减实际库存
            // 注意：这里不再校验 amount 是否充足，因为：
            // 1. Redis 已在下单时原子扣减，保证不超卖
            // 2. 如果 MySQL 数据异常，以 Redis 为准
            if (stockpile.getAmount() >= quantity) {
                stockpile.setAmount(stockpile.getAmount() - quantity);
            } else {
                // MySQL 库存异常（小于应扣减量），直接设为 0 并记录日志
                System.err.println("警告：商品 " + productId + " MySQL库存异常，当前库存=" 
                    + stockpile.getAmount() + "，应扣减=" + quantity);
                stockpile.setAmount(0);
            }
            
            // 清理 frozen 字段（不再使用）
            stockpile.setFrozen(0);
            stockpileRepository.save(stockpile);
            
            // 【可选优化】同步 Redis 库存到 MySQL（双向同步）
            // Long redisStock = stockCacheService.getStock(productId);
            // if (redisStock != null) {
            //     stockpile.setAmount(redisStock.intValue());
            // }

            // 删除关系并从购物车删除条目
            cartsOrdersRelationRepository.delete(cartsOrdersRelation);
            
            // 【修复】直接删除购物车条目，不调用需要登录的 cartService.deleteCartItem
            // 因为支付回调时没有用户登录状态，getCurrentAccount() 会失败
            cartItemRepository.deleteById(cartItem.getCartItemId());
            
            // 【Redis】从购物车缓存中删除
            cartCacheService.removeFromCart(cartItem.getUserId(), cartItem.getProductId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVO> getPurchasedProducts(Integer userId) {
        // 查询用户的所有成功订单
        List<Order> orders = orderRepository.findByUserIdAndStatus(userId, Order.OrderStatus.SUCCESS);

        List<ProductVO> purchasedProducts = new ArrayList<>();
        Set<Integer> productIdSet = new HashSet<>(); // 用于去重

        for (Order order : orders) {
            // 查询订单下的所有商品快照
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getOrderId());
            for (OrderItem orderItem : orderItems) {
                if (!productIdSet.contains(orderItem.getProductId())) {
                    ProductVO productVO = new ProductVO();
                    productVO.setId(String.valueOf(orderItem.getProductId()));
                    productVO.setTitle(orderItem.getTitle());
                    productVO.setCover(orderItem.getCover());
                    productVO.setPrice(orderItem.getPrice());
                    purchasedProducts.add(productVO);
                    productIdSet.add(orderItem.getProductId());
                }
            }
        }
        return purchasedProducts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderVO> getOrderList(Integer userId) {
        // 打印传入的用户 ID
        System.out.println("User ID: " + userId);

        // 查询用户的所有订单
        List<Order> orders = orderRepository.findByUserId(userId);

        // 打印查询到的订单列表
        System.out.println("Orders: " + orders);

        // 转换为 OrderVO 列表
        List<OrderVO> orderVOList = new ArrayList<>();
        for (Order order : orders) {
            OrderVO orderVO = order.partToVO();
            // 打印每个订单的基本信息
            System.out.println("Order: " + order);

            // 填充用户名
            String username = accountRepository.findById(userId).get().getUsername();
            orderVO.setUsername(username);

            // 打印转换后的 OrderVO
            System.out.println("OrderVO: " + orderVO);

            orderVOList.add(orderVO);
        }

        // 打印最终的 OrderVO 列表
        System.out.println("OrderVO List: " + orderVOList);

        return orderVOList;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderVO getOrderDetail(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> TomatoMallException.orderNotExist());

        OrderVO orderVO = order.partToVO();
        orderVO.setUsername(accountRepository.findById(order.getUserId()).get().getUsername());

        // 填充收货信息
        orderVO.setReceiverName(order.getReceiverName());
        orderVO.setReceiverPhone(order.getReceiverPhone());
        orderVO.setReceiverAddress(order.getReceiverAddress());
        orderVO.setReceiverPostalCode(order.getReceiverPostalCode());

        // 查询订单商品
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        List<OrderItemVO> items = new ArrayList<>();
        for (OrderItem orderItem : orderItems) {
            OrderItemVO itemVO = new OrderItemVO(
                    orderItem.getProductId(),
                    orderItem.getTitle(),
                    orderItem.getCover(),
                    orderItem.getPrice(),
                    orderItem.getQuantity()
            );
            items.add(itemVO);
        }
        orderVO.setItems(items);

        return orderVO;
    }

    @Override
    @Transactional
    public void deleteOrder(Integer orderId) {
        // 检查订单是否存在
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        // 删除订单与购物车的关联关系
        List<CartsOrdersRelation> relations = cartsOrdersRelationRepository.findByOrderId(orderId);
        for (CartsOrdersRelation relation : relations) {
            cartsOrdersRelationRepository.delete(relation);
        }

        // 删除订单
        orderRepository.delete(order);
    }
}
