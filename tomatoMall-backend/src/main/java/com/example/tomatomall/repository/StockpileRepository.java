package com.example.tomatomall.repository;
import com.example.tomatomall.po.Stockpile;
import org.springframework.data.jpa.repository.*;
public interface StockpileRepository extends JpaRepository<Stockpile,Integer> {
    Stockpile findByProductId(int productId);
    @Modifying @Query("update Stockpile s set s.frozen=s.frozen+:quantity where s.product.id=:productId and :quantity>0 and s.amount-s.frozen>=:quantity")
    int reserve(Integer productId,int quantity);
    @Modifying @Query("update Stockpile s set s.amount=s.amount-:quantity, s.frozen=s.frozen-:quantity where s.product.id=:productId and :quantity>0 and s.frozen>=:quantity and s.amount>=:quantity")
    int consume(Integer productId,int quantity);
    @Modifying @Query("update Stockpile s set s.frozen=s.frozen-:quantity where s.product.id=:productId and :quantity>0 and s.frozen>=:quantity")
    int release(Integer productId,int quantity);
    @Modifying @Query("update Stockpile s set s.amount=:amount where s.product.id=:productId and :amount>=s.frozen and :amount>=0")
    int changeAmount(Integer productId,int amount);
}
