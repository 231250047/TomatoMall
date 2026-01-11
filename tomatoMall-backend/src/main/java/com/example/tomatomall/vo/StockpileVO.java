package com.example.tomatomall.vo;



import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class StockpileVO {
    private Integer id;
    private int productId;
    private int amount;
    private int frozen;
}
