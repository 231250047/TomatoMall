package com.example.tomatomall.service;

import com.example.tomatomall.vo.FavoriteVO;
import com.example.tomatomall.vo.Response;

import java.util.List;

public interface FavoriteService {
    
    /**
     * 添加收藏
     */
    Response<String> addFavorite(Integer accountId, Integer productId);
    
    /**
     * 取消收藏
     */
    Response<String> removeFavorite(Integer accountId, Integer productId);
    
    /**
     * 获取收藏列表
     */
    Response<List<FavoriteVO>> getFavoriteList(Integer accountId);
    
    /**
     * 检查是否已收藏
     */
    Response<Boolean> isFavorited(Integer accountId, Integer productId);
    
    /**
     * 获取收藏数量
     */
    Response<Integer> getFavoriteCount(Integer accountId);
}