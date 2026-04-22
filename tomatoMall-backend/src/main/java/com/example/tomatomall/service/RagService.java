package com.example.tomatomall.service;

import com.example.tomatomall.po.Product;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * RAG服务接口 - 真正的RAG实现
 * 包含:文档加载、向量化、召回、重排序
 */
public interface RagService {

    /**
     * 构建向量知识库
     * 从数据库加载商品数据并向量化存储
     */
    void buildVectorKnowledgeBase();

    /**
     * RAG检索 - 召回 + 重排
     * @param query 用户查询
     * @param topK 返回多少个相关文档
     * @return 检索到的文档列表
     */
    List<Document> retrieveDocuments(String query, int topK);

    /**
     * 获取增强的推荐内容
     * @param query 用户查询
     * @return AI可以理解的推荐文�?
     */
    String getEnhancedRecommendation(String query);

    /**
     * 将Product转换为Document
     */
    Document productToDocument(Product product);
}
