package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.*;
import com.example.tomatomall.repository.*;
import com.example.tomatomall.service.CartService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.ProductService;
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

    // 设置订单锁定超时时间（毫秒），30分钟
    private static final long ORDER_LOCK_TIMEOUT = 30 * 60 * 1000;

    @Override
    @Transactional
    public OrderVO createOrder(String userName, CheckoutVO checkoutVO) {
        // 1. 获取购物车商品
        List<CartItem> cartItemList = new ArrayList<>();
        for (String cartItemId : checkoutVO.getCartItemIds()) {
            Integer cartItemIdInt = Integer.parseInt(cartItemId);
            if (!cartItemRepository.existsById(cartItemIdInt)) {
                throw TomatoMallException.cartItemNotExist();
            } else
                cartItemList.add(cartItemRepository.findById(cartItemIdInt).get());
        }
        // 2. 验证库存并锁定
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cartItemList) {
            Stockpile stockpile;
            if (!productRepository.existsById(cartItem.getProductId())) {
                throw TomatoMallException.productNotExist();
            } else
                stockpile = stockpileRepository.findByProductId(cartItem.getProductId());
            if (stockpile.getAmount() - stockpile.getFrozen() < cartItem.getQuantity()) {
                throw TomatoMallException.stockpileNotEnough();
            } else
                totalAmount = totalAmount
                        .add(stockpile.getProduct().getPrice().multiply(new BigDecimal(cartItem.getQuantity())));

            // 锁定库存:将所需数量添加到冻结库存中
            stockpile.setFrozen(stockpile.getFrozen() + cartItem.getQuantity());
            stockpileRepository.save(stockpile);
        }
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
        // 3. 创建订单
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
     * 释放订单锁定的库存（保护性处理，不会删除订单或关系）
     * 可以由定时任务调用处理超时订单
     */
    @Transactional
    public void releaseLockedStock(String orderId) {
        List<CartsOrdersRelation> relations = cartsOrdersRelationRepository.findByOrderId(Integer.parseInt(orderId));
        for (CartsOrdersRelation relation : relations) {
            CartItem cartItem = cartItemRepository.getById(relation.getCartitemId());
            Stockpile stockpile = stockpileRepository.findByProductId(cartItem.getProductId());

            // 减少冻结库存，但不减少总库存；保护性处理，防止负值
            int newFrozen = stockpile.getFrozen() - cartItem.getQuantity();
            if (newFrozen < 0)
                newFrozen = 0;
            stockpile.setFrozen(newFrozen);
            stockpileRepository.save(stockpile);
        }
    }

    /**
     * 在下单或支付失败时彻底取消订单：释放冻结库存、删除订单关系并删除订单本身
     * 不删除购物车中的条目（保留用户购物车）
     */
    @Transactional
    public void cancelOrder(String orderId) {
        int oid = Integer.parseInt(orderId);
        List<CartsOrdersRelation> relations = cartsOrdersRelationRepository.findByOrderId(oid);
        for (CartsOrdersRelation relation : relations) {
            CartItem cartItem = cartItemRepository.getById(relation.getCartitemId());
            Stockpile stockpile = stockpileRepository.findByProductId(cartItem.getProductId());

            // 释放冻结库存，保护性处理，防止负值
            int newFrozen = stockpile.getFrozen() - cartItem.getQuantity();
            if (newFrozen < 0)
                newFrozen = 0;
            stockpile.setFrozen(newFrozen);
            stockpileRepository.save(stockpile);

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
        Order order = orderRepository.getById(Integer.parseInt(orderId));
        if (!order.getStatus().equals(Order.OrderStatus.SUCCESS)) {
            order.setStatus(Order.OrderStatus.SUCCESS);
            order.setPaymentTime(new Date());  // 新增：设置支付时间为当前时间
            orderRepository.save(order);
            // 支付成功后立即减库存（已在事务中，保证一致性）
            reduceStock(orderId);
        }
    }

    @Override
    @Transactional
    public void reduceStock(String orderId) {
        List<CartsOrdersRelation> cartsOrdersRelationList = cartsOrdersRelationRepository
                .findByOrderId(Integer.parseInt(orderId));
        for (CartsOrdersRelation cartsOrdersRelation : cartsOrdersRelationList) {
            CartItem cartItem = cartItemRepository.getById(cartsOrdersRelation.getCartitemId());
            Product product = productRepository.getById(cartItem.getProductId());

            // 获取商品库存
            Stockpile stockpile = stockpileRepository.findByProductId(product.getId());

            // 校验库存与冻结库存，避免出现负值或超卖
            if (stockpile.getAmount() < cartItem.getQuantity()) {
                // 发现异常库存状态，抛出异常以触发事务回滚和告警
                throw TomatoMallException.stockpileNotEnough();
            }
            if (stockpile.getFrozen() < cartItem.getQuantity()) {
                // 冻结库存不足表示业务不一致，直接修正为0并继续（也可选择抛异常）
                stockpile.setFrozen(0);
            } else {
                stockpile.setFrozen(stockpile.getFrozen() - cartItem.getQuantity());
            }

            // 减少实际库存
            stockpile.setAmount(stockpile.getAmount() - cartItem.getQuantity());
            stockpileRepository.save(stockpile);

            // 删除关系并从购物车删除条目
            cartsOrdersRelationRepository.delete(cartsOrdersRelation);
            cartService.deleteCartItem(cartItem.getCartItemId().toString());
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
