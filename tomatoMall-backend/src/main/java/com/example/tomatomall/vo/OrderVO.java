package com.example.tomatomall.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class OrderVO {
    private int orderId;
    private String username;//样例这么给的，sb样例
    private BigDecimal totalAmount;
    private String paymentMethod;
    private Date createTime;
    private String status;
    private Date paymentTime;  // 新增：支付时间

    // 收货信息
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String receiverPostalCode;

    // 商品列表
    private List<OrderItemVO> items;
}
