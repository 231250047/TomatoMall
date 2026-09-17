package com.example.tomatomall.service;

import com.example.tomatomall.vo.AdvertisementVO;
import java.util.List;

/**
 * 广告缓存服务接口
 * 
 * 【Redis 数据结构选型】List
 * 选择 List 的原因:
 * 1. 广告有顺序要求(轮播顺序�?
 * 2. 需要支持从两端插入/删除(新广告置顶或置底)
 * 3. 可以通过索引快速访问特定位置的广告
 * 
 * 【缓�?Key 设计�?
 * - 首页广告列表:ad:homepage:list
 * 
 * 【面试要点�?
 * - List vs Set vs ZSet 的选型依据
 * - 首页广告的缓存更新策�?
 */
public interface AdvertisementCacheService {
    
    /**
     * 获取首页广告列表(优先缓存)
     * @return 广告列表
     */
    List<AdvertisementVO> getAdsWithCache();
    
    /**
     * 刷新广告缓存
     * 【使用场景】广告增删改后调�?
     */
    void refreshAdsCache();
    
    /**
     * 删除广告缓存
     */
    void deleteAdsCache();
}
