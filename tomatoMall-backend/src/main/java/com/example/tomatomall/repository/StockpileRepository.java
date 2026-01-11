package com.example.tomatomall.repository;

import com.example.tomatomall.po.Stockpile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockpileRepository extends JpaRepository<Stockpile, Integer> {
    Stockpile findByProductId(int productId);
}