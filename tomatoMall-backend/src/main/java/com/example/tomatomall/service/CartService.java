package com.example.tomatomall.service;

import com.example.tomatomall.vo.CartItemVO;
import com.example.tomatomall.vo.CartListVO;

import java.util.List;

public interface CartService {
    String addToCart(String productId, Integer quantity);
    String deleteCartItem(String cartItemId);
    String updateQuantity(String cartItemId, Integer quantity);
    CartListVO listCartItems();
}
