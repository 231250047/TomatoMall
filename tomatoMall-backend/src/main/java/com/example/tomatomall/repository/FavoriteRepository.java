package com.example.tomatomall.repository;

import com.example.tomatomall.po.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {
    
    /**
     * 根据用户ID和商品ID查找收藏记录
     */
    Optional<Favorite> findByAccountIdAndProductId(Integer accountId, Integer productId);
    
    /**
     * 根据用户ID获取收藏列表
     */
    List<Favorite> findByAccountIdOrderByCreateTimeDesc(Integer accountId);
    
    /**
     * 检查是否已收藏
     */
    boolean existsByAccountIdAndProductId(Integer accountId, Integer productId);
    
    /**
     * 删除收藏记录
     */
    void deleteByAccountIdAndProductId(Integer accountId, Integer productId);
    
    /**
     * 统计用户收藏数量
     */
    Integer countByAccountId(Integer accountId);
}
