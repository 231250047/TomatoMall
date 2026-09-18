package com.example.tomatomall.repository;

import com.example.tomatomall.po.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
    List<CartItem> findByUserId(Integer userId);
    //Optional 存在则返回值，否则返回null
    Optional<CartItem> findByUserIdAndProductId(Integer userId, Integer productId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from CartItem o where o.cartItemId = :id")
    java.util.Optional<CartItem> lockById(Integer id);
}
