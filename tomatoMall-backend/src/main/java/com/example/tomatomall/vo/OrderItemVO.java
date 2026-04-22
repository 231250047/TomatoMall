package com.example.tomatomall.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class OrderItemVO {
    private Integer productId;
    private String title;
    private String cover;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal; // 小计 = 单价 × 数量

    public OrderItemVO(Integer productId, String title, String cover,
                       BigDecimal price, Integer quantity) {
        this.productId = productId;
        this.title = title;
        this.cover = cover;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = price.multiply(new BigDecimal(quantity));
    }
}
