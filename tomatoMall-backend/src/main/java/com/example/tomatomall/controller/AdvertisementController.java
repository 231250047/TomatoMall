package com.example.tomatomall.controller;

import com.example.tomatomall.service.AdvertisementCacheService;
import com.example.tomatomall.service.AdvertisementService;
import com.example.tomatomall.vo.AdvertisementVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 广告控制器
 * 
 * 【Redis 集成说明】
 * 1. 使用 Redis List 缓存广告列表
 * 2. 增删改后自动刷新缓存
 */
@RestController
@RequestMapping("/api/advertisements")
public class AdvertisementController {

    @Resource
    AdvertisementService advertisementService;
    
    // ========== Redis 缓存服务 ==========
    @Autowired
    private AdvertisementCacheService advertisementCacheService;

    /**
     * 获取所有广�?
     * 
     * 【Redis 功能�?
     * 优先�?Redis List 缓存获取，缓存未命中则从数据库加�?
     */
    @GetMapping
    public Response<List<AdvertisementVO>> getAllAds() {
        // 【Redis】使用缓存获取广告列�?
        List<AdvertisementVO> ads = advertisementCacheService.getAdsWithCache();
        return Response.buildSuccess(ads);
    }

    /**
     * 创建广告
     * 
     * 【Redis 功能�?
     * 创建后刷新缓�?
     */
     @PostMapping
     public Response<AdvertisementVO> createAd(@RequestBody AdvertisementVO advertisementVO) {
         System.out.println("Id"+advertisementVO.getId());
         System.out.println("Title"+advertisementVO.getTitle());
            System.out.println("Content"+advertisementVO.getContent());
            System.out.println("Image URL"+advertisementVO.getImgUrl());
         AdvertisementVO result = advertisementService.createAd(advertisementVO);
         
         // 【Redis】刷新广告缓�?
         advertisementCacheService.refreshAdsCache();
         
         return Response.buildSuccess(result);
     }

    /**
     * 更新广告
     * 
     * 【Redis 功能�?
     * 更新后刷新缓�?
     */
    @PutMapping
    public Response<String> updateAd(@RequestBody AdvertisementVO advertisementVO) {
        String result = advertisementService.updateAd(advertisementVO);
        
        // 【Redis】刷新广告缓�?
        advertisementCacheService.refreshAdsCache();
        
        return Response.buildSuccess(result);
    }

    /**
     * 删除广告
     * 
     * 【Redis 功能�?
     * 删除后刷新缓�?
     */
    @DeleteMapping("/{id}")
    public Response<String> deleteAd(@PathVariable Integer id) {
        String result = advertisementService.deleteAd(id);
        
        // 【Redis】刷新广告缓�?
        advertisementCacheService.refreshAdsCache();
        
        return Response.buildSuccess(result);
    }
}
