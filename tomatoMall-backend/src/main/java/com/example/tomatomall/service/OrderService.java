package com.example.tomatomall.service;

import com.example.tomatomall.vo.CheckoutVO;
import com.example.tomatomall.vo.OrderVO;
import com.example.tomatomall.vo.ProductVO;

import java.util.List;

public interface OrderService {
    OrderVO createOrder(String userName, CheckoutVO checkoutVO);

    void updateOrderStatus(String orderId, String alipayTradeNo, String amount);


    List<ProductVO> getPurchasedProducts(Integer userId);

    /**
     * 获取用户的订单列�?
     */
    List<OrderVO> getOrderList(Integer userId);

    OrderVO getOrderDetail(Integer orderId);

    void deleteOrder(Integer orderId);
}
