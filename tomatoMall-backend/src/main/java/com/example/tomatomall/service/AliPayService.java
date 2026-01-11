package com.example.tomatomall.service;


import com.example.tomatomall.po.AliPayOrder;

public interface AliPayService {
    AliPayOrder createAliPayOrder(Integer orderId);
}
