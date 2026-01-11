package com.example.tomatomall.po;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AliPayOrder {
    String paymentForm;
    Integer orderId;
    BigDecimal totalAmount;
    String paymentMethod;
}