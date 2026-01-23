package com.example.tomatomall.service;

import java.util.List;
import java.util.Map;

/**
 * 商品热搜榜服务接口
 * 
 * 【Redis 数据结构选型】ZSet (有序集合)
 * Key: search:hot:keywords 或 product:hot
 * Member: 搜索词/商品ID
 * Score: 热度分数（搜索次数/浏览次数）
 * 
 * 【为什么选择 ZSet】
 * 1. 自动排序：按 score 排序，天然适合排行榜
 * 2. 原子递增：ZINCRBY 原子增加分数，高并发安全
 * 3. 范围查询：ZREVRANGE 获取 Top N，O(log(N)+M) 复杂度
 * 4. 去重：member 唯一，不会重复统计
 * 
 * 【ZSet vs 其他方案对比】
 * 1. MySQL ORDER BY: 每次都要排序，性能差
 * 2. Redis List: 需要手动维护顺序，不支持分数
 * 3. Redis Hash: 不支持排序
 * 
 * 【面试深度解析】
 * Q: ZSet 底层实现？
 * A: 跳表(SkipList) + 哈希表
 *    - 跳表：支持有序遍历，范围查询 O(log N)
 *    - 哈希表：支持 O(1) 根据 member 查 score
 * 
 * Q: 为什么用跳表不用红黑树？
 * A: 
 *    1. 跳表实现更简单
 *    2. 范围查询更高效（顺序遍历）
 *    3. 并发友好（局部锁）
 */
public interface HotRankService {
    
    /**
     * 记录搜索词热度
     * @param keyword 搜索关键词
     */
    void recordSearch(String keyword);
    
    /**
     * 记录商品浏览热度
     * @param productId 商品ID
     */
    void recordProductView(Integer productId);
    
    /**
     * 获取热搜关键词排行榜
     * @param topN 获取前N个
     * @return 关键词列表（按热度降序）
     */
    List<String> getHotKeywords(int topN);
    
    /**
     * 获取热搜关键词及分数
     * @param topN 获取前N个
     * @return Map<关键词, 分数>
     */
    Map<String, Double> getHotKeywordsWithScore(int topN);
    
    /**
     * 获取热门商品排行榜
     * @param topN 获取前N个
     * @return 商品ID列表（按热度降序）
     */
    List<Integer> getHotProducts(int topN);
    
    /**
     * 获取热门商品及分数
     * @param topN 获取前N个
     * @return Map<商品ID, 分数>
     */
    Map<Integer, Double> getHotProductsWithScore(int topN);
    
    /**
     * 获取关键词热度排名
     * @param keyword 关键词
     * @return 排名（从0开始），null表示不在榜上
     */
    Long getKeywordRank(String keyword);
    
    /**
     * 获取商品热度排名
     * @param productId 商品ID
     * @return 排名（从0开始），null表示不在榜上
     */
    Long getProductRank(Integer productId);
    
    /**
     * 清理热度数据（保留Top N）
     * @param keepTopN 保留的数量
     */
    void cleanHotKeywords(int keepTopN);
    
    /**
     * 清理商品热度数据
     * @param keepTopN 保留的数量
     */
    void cleanHotProducts(int keepTopN);
}
