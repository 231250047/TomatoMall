package com.example.tomatomall.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CartListVO {
    private List<CartItemVO> items;
    private Integer total;
    private BigDecimal totalAmount;


}
