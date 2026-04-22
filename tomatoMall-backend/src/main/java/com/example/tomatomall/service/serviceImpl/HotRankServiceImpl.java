package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.service.HotRankService;
import com.example.tomatomall.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品热搜榜服务实�?
 * 
 * 【Redis 数据结构】ZSet
 * 
 * 热搜关键词:
 * Key: search:hot:keywords
 * Member: keyword (搜索�?
 * Score: 搜索次数
 * 
 * 热门商品�?
 * Key: product:hot
 * Member: productId (商品ID)
 * Score: 浏览次数
 * 
 * 【ZSet 核心操作�?
 * 1. ZINCRBY: 原子增加分数(每次搜�?浏览 +1�?
 * 2. ZREVRANGE: 按分数从高到低获取(获取排行榜)
 * 3. ZREVRANK: 获取元素排名(查询某个词的排名)
 * 4. ZREMRANGEBYRANK: 按排名删除(清理低热度数据)
 * 
 * 【实时排行榜实现原理�?
 * ```
 * // 用户搜索 "iPhone"
 * ZINCRBY search:hot:keywords 1 "iPhone"
 * 
 * // 获取 Top 10 热搜
 * ZREVRANGE search:hot:keywords 0 9 WITHSCORES
 * 返回: [("iPhone", 1000), ("华为", 800), ("小米", 600), ...]
 * ```
 * 
 * 【面试亮点�?
 * 1. ZSet 实现实时排行�?
 * 2. ZINCRBY 原子操作保证高并发安�?
 * 3. 定时清理机制防止数据无限增长
 * 4. 支持带分数的排行榜展�?
 */
@Service
public class HotRankServiceImpl implements HotRankService {
    
    // 热搜关键�?Key
    private static final String HOT_KEYWORDS_KEY = "search:hot:keywords";
    // 热门商品 Key
    private static final String HOT_PRODUCTS_KEY = "product:hot";
    
    @Autowired
    private RedisService redisService;
    
    /**
     * 记录搜索词热�?
     * 
     * 【核心方法�?
     * 每次搜索调用 ZINCRBY，分�?+1
     * ZSet 自动维护排序，无需额外操作
     */
    @Override
    public void recordSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        // 标准化处理:去空格、转小写
        String normalizedKeyword = keyword.trim().toLowerCase();
        
        // ZINCRBY: 如果 member 不存在，初始化为 0 再增�?
        redisService.zIncrScore(HOT_KEYWORDS_KEY, normalizedKeyword, 1);
    }
    
    /**
     * 记录商品浏览热度
     * 
     * 【使用场景�?
     * 用户浏览商品详情页时调用
     */
    @Override
    public void recordProductView(Integer productId) {
        if (productId == null) {
            return;
        }
        redisService.zIncrScore(HOT_PRODUCTS_KEY, productId.toString(), 1);
    }
    
    /**
     * 获取热搜关键词排行榜
     * 
     * 【Redis 命令】ZREVRANGE
     * 按分数从高到低返回指定范围的 member
     * 
     * 【时间复杂度】O(log(N) + M)
     * N: ZSet 元素总数
     * M: 返回的元素数�?
     */
    @Override
    public List<String> getHotKeywords(int topN) {
        Set<Object> keywords = redisService.zReverseRange(HOT_KEYWORDS_KEY, 0, topN - 1);
        if (keywords == null) {
            return new ArrayList<>();
        }
        return keywords.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取热搜关键词及分数
     * 
     * 【Redis 命令】ZREVRANGE ... WITHSCORES
     * 同时返回 member �?score
     */
    @Override
    public Map<String, Double> getHotKeywordsWithScore(int topN) {
        Map<Object, Double> rawMap = redisService.zReverseRangeWithScores(HOT_KEYWORDS_KEY, 0, topN - 1);
        Map<String, Double> result = new LinkedHashMap<>(); // 保持顺序
        
        if (rawMap != null) {
            for (Map.Entry<Object, Double> entry : rawMap.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        
        return result;
    }
    
    /**
     * 获取热门商品排行�?
     */
    @Override
    public List<Integer> getHotProducts(int topN) {
        Set<Object> products = redisService.zReverseRange(HOT_PRODUCTS_KEY, 0, topN - 1);
        if (products == null) {
            return new ArrayList<>();
        }
        return products.stream()
                .map(obj -> Integer.parseInt(obj.toString()))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取热门商品及分�?
     */
    @Override
    public Map<Integer, Double> getHotProductsWithScore(int topN) {
        Map<Object, Double> rawMap = redisService.zReverseRangeWithScores(HOT_PRODUCTS_KEY, 0, topN - 1);
        Map<Integer, Double> result = new LinkedHashMap<>();
        
        if (rawMap != null) {
            for (Map.Entry<Object, Double> entry : rawMap.entrySet()) {
                try {
                    result.put(Integer.parseInt(entry.getKey().toString()), entry.getValue());
                } catch (NumberFormatException e) {
                    // 跳过无效数据
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取关键词热度排�?
     * 
     * 【Redis 命令】ZREVRANK
     * 返回 member �?ZSet 中的排名(按分数从高到低�?
     * 排名�?0 开�?
     */
    @Override
    public Long getKeywordRank(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String normalizedKeyword = keyword.trim().toLowerCase();
        return redisService.zReverseRank(HOT_KEYWORDS_KEY, normalizedKeyword);
    }
    
    /**
     * 获取商品热度排名
     */
    @Override
    public Long getProductRank(Integer productId) {
        if (productId == null) {
            return null;
        }
        return redisService.zReverseRank(HOT_PRODUCTS_KEY, productId.toString());
    }
    
    /**
     * 清理热搜数据(保�?Top N�?
     * 
     * 【Redis 命令】ZREMRANGEBYRANK
     * 删除排名在指定范围内的元�?
     * 
     * 【定时任务建议�?
     * 可以配合 @Scheduled 定时执行，防止数据无限增�?
     * 例如:每天凌晨清理，保留 Top 1000
     */
    @Override
    public void cleanHotKeywords(int keepTopN) {
        Long size = redisService.zSize(HOT_KEYWORDS_KEY);
        if (size != null && size > keepTopN) {
            // 删除排名�?keepTopN 之后的所有元�?
            // 排名�?0 开始，所以删�?[keepTopN, -1]
            redisService.zRemoveRange(HOT_KEYWORDS_KEY, keepTopN, -1);
        }
    }
    
    /**
     * 清理商品热度数据
     */
    @Override
    public void cleanHotProducts(int keepTopN) {
        Long size = redisService.zSize(HOT_PRODUCTS_KEY);
        if (size != null && size > keepTopN) {
            redisService.zRemoveRange(HOT_PRODUCTS_KEY, keepTopN, -1);
        }
    }
}
