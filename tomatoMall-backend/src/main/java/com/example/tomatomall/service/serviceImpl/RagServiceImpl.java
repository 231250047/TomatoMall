package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG服务实现 - 使用阿里云Qwen3-Embedding
 * 包含:文档加载、向量化、召回、重排序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final ProductRepository productRepository;
    private VectorStore vectorStore;  // Chroma向量数据库

    /**
     * 构建向量知识库
     * 这是RAG的第一步:将商品数据向量化并存储到Chroma
     */
    @Override
    @Transactional(readOnly = true)
    public void buildVectorKnowledgeBase() {
        log.info("开始构建向量知识库(使用SimpleVectorStore)...");

        try {
            // 1. 从数据库加载商品数据
            List<Product> products = productRepository.findAll();
            log.info("加载了{}个商品", products.size());

            // 2. 将商品转换为Document
            List<Document> documents = products.stream()
                .map(this::productToDocument)
                .collect(Collectors.toList());

            // 3. 存储到SimpleVectorStore向量数据库
            vectorStore.add(documents);
            log.info("向量知识库构建完成!共存储{}个文档到SimpleVectorStore", documents.size());

        } catch (Exception e) {
            log.error("构建向量知识库失败");
            throw new RuntimeException("构建向量知识库失败");
        }
    }

    /**
     * RAG检�?- 召回 + 重排(使用Chroma向量数据库)
     * @param query 用户查询
     * @param topK 返回多少个相关文�?
     * @return 检索到的文档列�?
     */
    @Override
    public List<Document> retrieveDocuments(String query, int topK) {
        log.info("开始RAG检索(Chroma)，查询:{}，topK:{}", query, topK);

        try {
            // 确保向量存储已初始化
            if (vectorStore == null) {
                log.warn("VectorStore未注入，尝试构建向量知识库");
                buildVectorKnowledgeBase();
            }

            // ============ 第一阶段:召�?============
            // 使用向量相似度进行召�?
            // 这里的相似度计算是基于embedding的余弦相似度
            SearchRequest request = SearchRequest.query(query)
                .withTopK(topK * 2)  // 召回更多候选(用于重排�?
                .withSimilarityThreshold(0.3);  // 相似度阈�?

            List<Document> recalledDocs = vectorStore.similaritySearch(request);
            log.info("召回阶段:检索到{}个候选文档", recalledDocs.size());

            // ============ 第二阶段:重�?============
            // 使用更复杂的策略重新排序
            List<Document> rerankedDocs = rerankDocuments(recalledDocs, query, topK);
            log.info("重排阶段:最终保留{}个最相关文档", rerankedDocs.size());

            return rerankedDocs;

        } catch (Exception e) {
            log.error("RAG检索失败");
            return Collections.emptyList();
        }
    }

    /**
     * 重排算法
     * 对召回的文档进行更精细的排序
     */
    private List<Document> rerankDocuments(List<Document> recalledDocs, String query, int topK) {
        log.info("开始重排，候选文档数:{}，目标topK:{}", recalledDocs.size(), topK);

        // 多维度评分重�?
        Map<Document, Double> docScores = new HashMap<>();

        for (Document doc : recalledDocs) {
            double score = 0.0;

            // 1. 关键词匹配得�?
            String content = doc.getContent().toLowerCase();
            String queryLower = query.toLowerCase();
            if (content.contains(queryLower)) {
                score += 0.3;  // 精确匹配加分
            }

            // 2. 商品评分加分
            if (content.contains("评分") || content.contains("rate")) {
                // 提取评分信息
                if (content.contains("9.")) {
                    score += 0.1;  // 高评分商品加�?
                }
            }

            // 3. 价格因素(低价商品可能更受欢迎)
            if (content.contains("价格") && (content.contains("10.") || content.contains("20."))) {
                score += 0.05;  // 适中价格加分
            }

            docScores.put(doc, score);
        }

        // 按综合得分排序，取topK�?
        return docScores.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))  // 降序排序
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * 获取增强的推荐内�?
     * 将检索到的文档转换为AI可以理解的格�?
     */
    @Override
    public String getEnhancedRecommendation(String query) {
        // 1. RAG检索(召回 + 重排�?
        List<Document> retrievedDocs = retrieveDocuments(query, 5);

        // 2. 构建增强内容
        StringBuilder enhancedContent = new StringBuilder();
        enhancedContent.append("基于您的需求").append(query).append("」，我从书店中找到了以下相关图书:\n\n");

        for (int i = 0; i < retrievedDocs.size(); i++) {
            Document doc = retrievedDocs.get(i);
            enhancedContent.append(String.format(
                "%d. %s\n",
                i + 1,
                doc.getContent()
            ));
        }

        enhancedContent.append("\n这些图书都是书店实际库存中的商品，请根据用户的实际需求进行推荐");

        return enhancedContent.toString();
    }

    /**
     * 将Product转换为Document
     * Document是Spring AI中表示文�?元数据的数据结构
     */
    @Override
    public Document productToDocument(Product product) {
        // 1. 构建文档文本(用于向量化
        String text = String.format("""
            书名�?s
            价格�?.2f�?
            评分�?.1f�?
            分类�?s
            简介:%s
            详情�?s
            """,
            product.getTitle(),
            product.getPrice(),
            product.getRate(),
            product.getTag(),
            product.getDescription() != null ? product.getDescription() : "暂无简介",
            product.getDetail() != null ? product.getDetail() : "暂无详情"
        );

        // 2. 构建元数据(用于过滤和重排)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", product.getId());
        metadata.put("title", product.getTitle());
        metadata.put("price", product.getPrice());
        metadata.put("rate", product.getRate());
        metadata.put("tag", product.getTag());

        // 3. 创建Document
        return new Document(text, metadata);
    }
}
