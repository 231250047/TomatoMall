package com.example.tomatomall.service;

import com.example.tomatomall.po.Product;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

/**
 * 向量检索服务 - 真正的RAG检索实现
 * 包含:向量化、相似度计算、召回、重排序
 */
public interface VectorRetrievalService {

    /**
     * 构建向量知识库
     * 将商品数据向量化存储
     */
    void buildVectorIndex();

    /**
     * 向量检索 - 核心RAG功能
     * @param query 用户查询
     * @param topK 返回前几个最相关的结果
     * @return 检索到的商品(带相似度得分)
     */
    List<Product> similaritySearch(String query, int topK);

    /**
     * 计算两个文本的相似度
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度得分(0-1之间�?
     */
    double calculateSimilarity(String text1, String text2);

    /**
     * 为查询生成向�?
     * @param query 查询文本
     * @return 文本的向量表�?
     */
    float[] embedQuery(String query);
}
