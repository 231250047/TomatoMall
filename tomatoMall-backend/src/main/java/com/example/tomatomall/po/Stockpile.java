package com.example.tomatomall.po;

import com.example.tomatomall.vo.StockpileVO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stockpiles")
public class Stockpile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @OneToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "product_id", referencedColumnName = "id", nullable = false)
    private Product product;

    @Basic
    @Column(name = "amount", nullable = false)
    private Integer amount=400;//默认�?00

    @Basic
    @Column(name = "frozen", nullable = false)
    private Integer frozen=0;
    public StockpileVO toVO(){
        StockpileVO stockpileVO = new StockpileVO();
        stockpileVO.setId(this.id);
        stockpileVO.setProductId(this.product.getId());
        stockpileVO.setAmount(this.amount);
        stockpileVO.setFrozen(this.frozen);
        return stockpileVO;
    }
}
