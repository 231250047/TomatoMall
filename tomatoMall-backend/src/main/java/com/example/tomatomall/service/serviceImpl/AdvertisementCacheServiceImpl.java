package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.AdvertisementCacheService;
import com.example.tomatomall.service.AdvertisementService;
import com.example.tomatomall.service.RedisService;
import com.example.tomatomall.vo.AdvertisementVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 广告缓存服务实现
 * 
 * 【Redis 数据结构】List
 * Key: ad:homepage:list
 * Value: 广告 JSON 列表
 * 
 * 【设计说明�?
 * 1. 使用 List 存储有序的广告列�?
 * 2. LRANGE 可以一次获取所有广告，适合全量读取场景
 * 3. 广告更新频率低，采用全量刷新策略
 * 
 * 【缓存更新策略�?
 * 广告增删改操作后，直接删除缓存(Cache Aside�?
 * 下次访问时重新加�?
 */
@Service
public class AdvertisementCacheServiceImpl implements AdvertisementCacheService {
    
    // 广告列表缓存 Key
    private static final String ADS_CACHE_KEY = "ad:homepage:list";
    // 缓存时间�?小时�?
    private static final long CACHE_TTL_MINUTES = 60;
    
    @Autowired
    private RedisService redisService;
    
    @Autowired
    private AdvertisementService advertisementService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 获取首页广告列表(优先缓存)
     * 
     * 【实现说明�?
     * 1. 先从 Redis List 获取缓存
     * 2. 缓存未命中则从数据库加载
     * 3. 将结果存�?Redis List
     * 
     * 【性能优化�?
     * 首页广告是高频访问数据，缓存命中率极�?
     */
    @Override
    public List<AdvertisementVO> getAdsWithCache() {
        // 1. 尝试从缓存获�?
        List<Object> cacheList = redisService.lRange(ADS_CACHE_KEY, 0, -1);
        
        if (cacheList != null && !cacheList.isEmpty()) {
            // 缓存命中，反序列化返�?
            return deserializeAdsList(cacheList);
        }
        
        // 2. 缓存未命中，从数据库加载
        List<AdvertisementVO> adsList = advertisementService.getAllAds();
        
        // 3. 存入缓存
        if (adsList != null && !adsList.isEmpty()) {
            cacheAdsList(adsList);
        }
        
        return adsList;
    }
    
    /**
     * 刷新广告缓存
     * 
     * 【使用场景�?
     * 后台管理员增删改广告后，主动刷新缓存
     */
    @Override
    public void refreshAdsCache() {
        // 先删除旧缓存
        deleteAdsCache();
        
        // 重新加载并缓�?
        List<AdvertisementVO> adsList = advertisementService.getAllAds();
        if (adsList != null && !adsList.isEmpty()) {
            cacheAdsList(adsList);
        }
    }
    
    /**
     * 删除广告缓存
     * 
     * 【Cache Aside Pattern�?
     * 数据变更时删除缓存，而非更新缓存
     */
    @Override
    public void deleteAdsCache() {
        redisService.delete(ADS_CACHE_KEY);
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 将广告列表存�?Redis List
     */
    private void cacheAdsList(List<AdvertisementVO> adsList) {
        // 先删除旧数据
        redisService.delete(ADS_CACHE_KEY);
        
        // 使用 RPUSH 逐个添加(保持顺序)
        for (AdvertisementVO ad : adsList) {
            try {
                String adJson = objectMapper.writeValueAsString(ad);
                redisService.lRightPush(ADS_CACHE_KEY, adJson);
            } catch (JsonProcessingException e) {
                // 序列化失败，跳过
            }
        }
        
        // 设置过期时间(加随机值防止雪崩)
        long randomTTL = CACHE_TTL_MINUTES + new Random().nextInt(10);
        redisService.expire(ADS_CACHE_KEY, randomTTL, TimeUnit.MINUTES);
    }
    
    /**
     * 反序列化广告列表
     */
    private List<AdvertisementVO> deserializeAdsList(List<Object> cacheList) {
        List<AdvertisementVO> result = new ArrayList<>();
        for (Object item : cacheList) {
            try {
                String json = item.toString();
                AdvertisementVO ad = objectMapper.readValue(json, AdvertisementVO.class);
                result.add(ad);
            } catch (JsonProcessingException e) {
                // 反序列化失败，跳�?
            }
        }
        return result;
    }
}
