package com.example.tomatomall.repository;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.vo.ProductVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    Product findByTitle(String title);

    Boolean existsByTitle(String title);

    // 根据分类标签查找商品
    List<Product> findByTag(String tag);

    // 查找评分最高的�?0个商�?
    List<Product> findTop10ByOrderByRateDesc();

    // 根据分类标签查找评分最高的�?0个商�?
    List<Product> findTop10ByTagOrderByRateDesc(String tag);

    // 根据sellerId查找商品
    List<Product> findBySellerId(Integer sellerId);

    List<Product> findByCreateTimeAfter(Date date);
}
