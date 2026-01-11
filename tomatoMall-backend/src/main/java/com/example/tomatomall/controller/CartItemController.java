package com.example.tomatomall.controller;

import com.example.tomatomall.Util.TokenUtil;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.service.CartService;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartItemController {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;
    @Autowired
    TokenUtil tokenUtil;

    @PostMapping
    public Response<String> addToCart(@RequestBody Map<String,String> addToCartRequest) {
        // 这里的addToCartRequest是一个Map，包含了productId和quantity
        String productId = addToCartRequest.get("productId");
        Integer quantity = Integer.parseInt(addToCartRequest.get("quantity"));
        return Response.buildSuccess(cartService.addToCart(productId, quantity));
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
    @PostMapping("/checkout")
    public Response<OrderVO> checkout(@RequestBody CheckoutVO checkoutRequest,  @RequestHeader("token") String token) {
        Account account = tokenUtil.getAccount(token);
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        System.out.println("Authentication"+authentication.toString());
//        String username = authentication.getName();
        return Response.buildSuccess(orderService.createOrder(account.getUsername(),checkoutRequest));
    }
}
