package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.VectorRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量检索服务实现
 * 包含:向量化、相似度计算、召回、重排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorRetrievalServiceImpl implements VectorRetrievalService {

    private final ProductRepository productRepository;
    private final EmbeddingModel embeddingModel;  // Spring AI的Embedding模型

    // 内存中的向量索引
    private Map<Integer, ProductWithEmbedding> vectorIndex = new HashMap<>();

    /**
     * 数据结构:商品 + 它的向量表示
     */
    private static class ProductWithEmbedding {
        Product product;
        float[] embedding;  // 商品文本的向量表示

        ProductWithEmbedding(Product product, float[] embedding) {
            this.product = product;
            this.embedding = embedding;
        }
    }

    /**
     * 构建向量知识库
     * 这是RAG的第一步:将所有商品向量化
     */
    @Override
    @Transactional(readOnly = true)
    public void buildVectorIndex() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           🏗️  开始构建向量知识库（RAG第一步）                   ║");
        log.info("╚════════════════════════════════════════════════════════════╝");

        try {
            // 1. 加载所有商品
            List<Product> products = productRepository.findAll();
            log.info("📦 从数据库加载了 {} 个商品", products.size());

            // 2. 批量向量化
            int batchSize = 10;  // 每次处理10个，避免API限流
            log.info("⚙️  批处理大小: {} 个商品/批次", batchSize);
            int totalBatches = (products.size() + batchSize - 1) / batchSize;
            log.info("⏳ 预计需要 {} 个批次", totalBatches);

            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < products.size(); i += batchSize) {
                int batchNum = (i / batchSize) + 1;
                int end = Math.min(i + batchSize, products.size());
                List<Product> batch = products.subList(i, end);

                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("🔄 处理批次 {}/{} (商品{}-{})",
                    batchNum, totalBatches, i + 1, end);

                // 批量生成embeddings(使用阿里云Qwen3-Embedding)
                List<String> texts = batch.stream()
                    .map(this::productToText)
                    .collect(Collectors.toList());

                // 调用阿里云DashScope Embedding API
                for (int j = 0; j < batch.size(); j++) {
                    Product product = batch.get(j);
                    try {
                        float[] embedding = embeddingModel.embed(texts.get(j));
                        vectorIndex.put(product.getId(), new ProductWithEmbedding(product, embedding));
                        successCount++;
                        log.info("✅ [{}] 商品向量化成功: {} (ID: {})",
                            successCount,
                            truncate(product.getTitle(), 30),
                            product.getId());
                    } catch (Exception e) {
                        failCount++;
                        log.error("❌ 商品向量化失败: {} (ID: {}), 错误: {}",
                            product.getTitle(),
                            product.getId(),
                            e.getMessage());
                    }
                }

                double progress = (end * 100.0) / products.size();
                log.info("📊 进度: {}/{} ({:.1f}%) | 成功: {} | 失败: {}",
                    end, products.size(), progress, successCount, failCount);
            }

            log.info("╔════════════════════════════════════════════════════════════╗");
            log.info("║           ✅ 向量知识库构建完成！                              ║");
            log.info("║           📊 总计: {} 个向量                                   ║", vectorIndex.size());
            log.info("║           ✅ 成功: {} 个                                       ║", successCount);
            if (failCount > 0) {
                log.info("║           ❌ 失败: {} 个                                       ║", failCount);
            }
            log.info("╚════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════╗");
            log.error("║           ❌ 向量知识库构建失败                                ║");
            log.error("╚════════════════════════════════════════════════════════════╝");
            log.error("错误类型: {}", e.getClass().getName());
            log.error("错误消息: {}", e.getMessage());
            log.error("堆栈跟踪:", e);
            throw new RuntimeException("向量知识库构建失败", e);
        }
    }

    /**
     * 向量检索 - RAG的核心功能
     * 包含:召回 + 重排
     */
    @Override
    public List<Product> similaritySearch(String query, int topK) {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           🔍 开始向量检索（RAG核心）                          ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("📝 查询内容: {}", query);
        log.info("🎯 TopK: {}", topK);

        try {
            // 确保向量索引已构建
            if (vectorIndex.isEmpty()) {
                log.info("⚠️  向量索引为空，开始构建...");
                buildVectorIndex();
            }

            // ============ 第一阶段:查询向量化 ============
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("📊 第一阶段:查询向量化");
            float[] queryEmbedding = embedQuery(query);
            log.info("✅ 查询向量维度: {}", queryEmbedding.length);

            // ============ 第二阶段:召回 ============
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("📊 第二阶段:召回（计算相似度）");
            // 计算查询向量与所有商品向量的相似度
            List<ProductWithScore> scoredProducts = new ArrayList<>();

            for (Map.Entry<Integer, ProductWithEmbedding> entry : vectorIndex.entrySet()) {
                ProductWithEmbedding pwe = entry.getValue();
                double similarity = cosineSimilarity(queryEmbedding, pwe.embedding);
                scoredProducts.add(new ProductWithScore(pwe.product, similarity));
            }

            // 按相似度排序
            scoredProducts.sort((a, b) -> Double.compare(b.score, a.score));

            // 召回topK*2个候选(用于重排)
            List<ProductWithScore> recalled = scoredProducts.stream()
                .limit(topK * 2)
                .collect(Collectors.toList());

            log.info("✅ 召回完成:检索到 {} 个候选商品", recalled.size());
            if (!recalled.isEmpty()) {
                log.info("📈 召回结果相似度范围: {:.4f} ~ {:.4f}",
                    recalled.get(recalled.size() - 1).score,
                    recalled.get(0).score);
            }

            // ============ 第三阶段:重排 ============
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("📊 第三阶段:重排（业务规则增强）");
            // 使用更多业务规则重新排序
            List<ProductWithScore> reranked = rerankProducts(recalled, query, topK);

            log.info("✅ 重排完成:最终保留 {} 个最相关商品", reranked.size());
            log.info("╔════════════════════════════════════════════════════════════╗");

            return reranked.stream()
                .map(ps -> ps.product)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════╗");
            log.error("║           ❌ 向量检索失败                                     ║");
            log.error("╚════════════════════════════════════════════════════════════╝");
            log.error("错误类型: {}", e.getClass().getName());
            log.error("错误消息: {}", e.getMessage());
            log.error("堆栈跟踪:", e);
            return Collections.emptyList();
        }
    }

    /**
     * 重排算法 - 业务逻辑增强
     */
    private List<ProductWithScore> rerankProducts(List<ProductWithScore> recalled, String query, int topK) {
        log.info("🔧 开始重排，候选商品数: {}，目标topK: {}", recalled.size(), topK);

        return recalled.stream()
            .map(ps -> {
                double finalScore = ps.score;  // 基础相似度得分
                double baseScore = ps.score;

                // 重排规则1:高评分商品加分
                if (ps.product.getRate() >= 9.0) {
                    finalScore += 0.1;
                    log.debug("📈 [{}] 高评分加分: {:.4f} -> {:.4f} (评分: {:.1f})",
                        truncate(ps.product.getTitle(), 20), baseScore, finalScore, ps.product.getRate());
                }

                // 重排规则2:价格合理加分
                if (ps.product.getPrice().compareTo(new java.math.BigDecimal("20.0")) >= 0 &&
                    ps.product.getPrice().compareTo(new java.math.BigDecimal("80.0")) <= 0) {
                    finalScore += 0.05;  // 适中价格加分
                    log.debug("📈 [{}] 价格合理加分: {:.4f} -> {:.4f} (价格: ¥{})",
                        truncate(ps.product.getTitle(), 20), baseScore, finalScore, ps.product.getPrice());
                }

                // 重排规则3:库存充足
                // 这里假设检查库存，实际需要查询Stockpile表
                // if (hasGoodStock(ps.product.getId())) {
                //     finalScore += 0.03;
                // }

                return new ProductWithScore(ps.product, finalScore);
            })
            .sorted((a, b) -> Double.compare(b.score, a.score))  // 重新排序
            .limit(topK)
            .collect(Collectors.toList());
    }

    /**
     * 计算余弦相似度 - 向量检索的核心算法
     * cosine_similarity = (A · B) / (||A|| * ||B||)
     */
    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 为查询生成向量(使用阿里云Qwen3-Embedding)
     */
    @Override
    public float[] embedQuery(String query) {
        try {
            return embeddingModel.embed(query);
        } catch (Exception e) {
            log.error("❌ 查询向量化失败", e);
            log.error("错误类型: {}", e.getClass().getName());
            log.error("错误消息: {}", e.getMessage());
            throw new RuntimeException("查询向量化失败", e);
        }
    }

    /**
     * 计算两个文本的相似度(使用阿里云Qwen3-Embedding)
     */
    @Override
    public double calculateSimilarity(String text1, String text2) {
        try {
            float[] emb1 = embeddingModel.embed(text1);
            float[] emb2 = embeddingModel.embed(text2);

            return cosineSimilarity(emb1, emb2);
        } catch (Exception e) {
            log.error("❌ 相似度计算失败", e);
            log.error("错误类型: {}", e.getClass().getName());
            log.error("错误消息: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 将商品转换为文本(用于向量化)
     */
    private String productToText(Product product) {
        return String.format("""
            书名:%s
            价格:￥%.2f
            评分:%.1f
            分类:%s
            简介:%s
            """,
            product.getTitle(),
            product.getPrice(),
            product.getRate(),
            product.getTag(),
            product.getDescription() != null ? product.getDescription() : "暂无简介"
        );
    }

    /**
     * 数据结构:商品 + 相似度得分
     */
    private static class ProductWithScore {
        Product product;
        double score;

        ProductWithScore(Product product, double score) {
            this.product = product;
            this.score = score;
        }
    }

    /**
     * 辅助方法:截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}
