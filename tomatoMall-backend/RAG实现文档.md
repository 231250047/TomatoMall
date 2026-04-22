# RAG实现完整文档

## 📋 目录

- [技术栈](#技术栈)
- [RAG流程概述](#rag流程概述)
- [详细实现步骤](#详细实现步骤)
- [重排机制详解](#重排机制详解)
- [核心代码说明](#核心代码说明)
- [配置说明](#配置说明)

---

## 🛠️ 技术栈

```
Spring AI 1.0.0-M4
├── Embedding模型: 阿里云DashScope (Qwen3-Embedding)
├── LLM: DeepSeek API (通过OpenAI兼容接口)
├── Vector Store: SimpleVectorStore (Spring AI内置内存向量存储)
├── Database: MySQL (商品数据存储)
└── Java: 17
```

### 依赖配置 (pom.xml)

```xml
<properties>
    <spring-ai.version>1.0.0-M4</spring-ai.version>
</properties>

<!-- Spring AI 核心依赖 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-core</artifactId>
    <version>${spring-ai.version}</version>
</dependency>

<!-- Spring AI OpenAI集成 (用于DeepSeek API) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>${spring-ai.version}</version>
</dependency>

<!-- 阿里云DashScope SDK (用于Qwen3-Embedding) -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>dashscope-sdk-java</artifactId>
    <version>2.16.7</version>
</dependency>
```

---

## 🎯 RAG流程概述

```
┌─────────────────────────────────────────────────────────────┐
│                     RAG完整流程                              │
└─────────────────────────────────────────────────────────────┘

【离线阶段】（应用启动时或定时更新）
1. 文档加载：从MySQL加载商品数据
2. 文档向量化：调用阿里云API生成向量
3. 向量存储：存入SimpleVectorStore

【在线阶段】（用户查询时）
4. 查询向量化：将用户查询转为向量
5. 相似度计算：计算查询与商品的余弦相似度
6. 召回：取topK*2个候选商品
7. 重排：结合业务规则重新排序
8. Prompt增强：将检索结果注入Prompt
9. LLM生成：使用DeepSeek生成推荐回复
10. 返回用户：格式化的推荐结果
```

---

## 📚 详细实现步骤

### 阶段1：初始化配置

**文件**: `VectorStoreConfig.java`

```java
@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.simple.store-path:src/main/resources/data/vectorstore}")
    private String storePath;

    private final EmbeddingModel embeddingModel;

    @Bean
    @Primary
    public VectorStore vectorStore() {
        log.info("初始化SimpleVectorStore...");
        log.info("向量存储路径: {}", storePath);

        // 创建SimpleVectorStore（使用默认构造函数）
        SimpleVectorStore vectorStore = new SimpleVectorStore(embeddingModel);

        log.info("SimpleVectorStore初始化成功！");
        return vectorStore;
    }
}
```

**作用**: 创建基于内存的向量存储，使用阿里云DashScope的Embedding模型进行向量化。

---

### 阶段2：文档加载

**文件**: `RagServiceImpl.java`

```java
@Override
@Transactional(readOnly = true)
public void buildVectorKnowledgeBase() {
    log.info("开始构建向量知识库...");

    try {
        // 1. 从数据库加载商品数据
        List<Product> products = productRepository.findAll();
        log.info("加载了{}个商品", products.size());

        // 2. 将商品转换为Document
        List<Document> documents = products.stream()
            .map(this::productToDocument)
            .collect(Collectors.toList());

        // 3. 存储到向量数据库
        vectorStore.add(documents);
        log.info("向量知识库构建完成！共存储{}个文档", documents.size());

    } catch (Exception e) {
        log.error("构建向量知识库失败", e);
        throw new RuntimeException("构建向量知识库失败");
    }
}
```

**数据转换** (`productToDocument`方法):

```java
@Override
public Document productToDocument(Product product) {
    // 1. 构建文档文本（用于向量化）
    String text = String.format("""
        书名：%s
        价格：%.2f元
        评分：%.1f分
        分类：%s
        简介：%s
        详情：%s
        """,
        product.getTitle(),
        product.getPrice(),
        product.getRate(),
        product.getTag(),
        product.getDescription() != null ? product.getDescription() : "暂无简介",
        product.getDetail() != null ? product.getDetail() : "暂无详情"
    );

    // 2. 构建元数据（用于过滤和重排）
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("id", product.getId());
    metadata.put("title", product.getTitle());
    metadata.put("price", product.getPrice());
    metadata.put("rate", product.getRate());
    metadata.put("tag", product.getTag());

    // 3. 创建Document
    return new Document(text, metadata);
}
```

**示例转换**:
```
数据库记录:
{
  id: 1,
  title: "Effective Java",
  price: 68.00,
  rate: 9.8,
  tag: "编程",
  description: "Java必读经典"
}
    ↓ 转换
Document文本:
"书名：Effective Java
 价格：68.00元
 评分：9.8分
 分类：编程
 简介：Java必读经典
 详情：暂无详情"
```

---

### 阶段3：文档向量化

**文件**: `VectorRetrievalServiceImpl.java`

```java
@Override
@Transactional(readOnly = true)
public void buildVectorIndex() {
    log.info("开始构建向量知识库...");

    try {
        // 1. 加载所有商品
        List<Product> products = productRepository.findAll();
        log.info("加载了{}个商品", products.size());

        // 2. 批量向量化（每次10个，避免API限流）
        int batchSize = 10;
        for (int i = 0; i < products.size(); i += batchSize) {
            int end = Math.min(i + batchSize, products.size());
            List<Product> batch = products.subList(i, end);

            // 批量生成embeddings
            List<String> texts = batch.stream()
                .map(this::productToText)
                .collect(Collectors.toList());

            // 调用阿里云DashScope Embedding API
            for (int j = 0; j < batch.size(); j++) {
                Product product = batch.get(j);
                float[] embedding = embeddingModel.embed(texts.get(j));
                vectorIndex.put(product.getId(),
                    new ProductWithEmbedding(product, embedding));
            }

            log.info("已处理{}/{}个商品", end, products.size());
        }

        log.info("向量知识库构建完成！共{}个向量", vectorIndex.size());

    } catch (Exception e) {
        log.error("构建向量知识库失败", e);
        throw new RuntimeException("向量知识库构建失败", e);
    }
}
```

**向量化过程**:
```
商品文本: "Effective Java 书价 68.0元 评分 9.5分 Java编程经典"
    ↓
[阿里云Qwen3-Embedding API]
    ↓
向量表示: [0.123, -0.456, 0.789, 0.234, ..., 0.567]
         (1024维浮点数数组)
```

---

### 阶段4：查询向量化

当用户发起查询时：

```java
@Override
public List<Product> similaritySearch(String query, int topK) {
    log.info("开始向量检索，查询：{}，topK：{}", query, topK);

    try {
        // 确保向量索引已构建
        if (vectorIndex.isEmpty()) {
            buildVectorIndex();
        }

        // 查询向量化
        float[] queryEmbedding = embedQuery(query);
        log.info("查询向量维度：{}", queryEmbedding.length);

        // ... 后续步骤
    } catch (Exception e) {
        log.error("向量检索失败", e);
        return Collections.emptyList();
    }
}

@Override
public float[] embedQuery(String query) {
    try {
        return embeddingModel.embed(query);
    } catch (Exception e) {
        log.error("查询向量化失败", e);
        throw new RuntimeException("查询向量化失败", e);
    }
}
```

**示例**:
```
用户查询: "推荐一些Java编程书籍"
    ↓
[阿里云Qwen3-Embedding API]
    ↓
查询向量: [0.234, -0.567, 0.890, 0.345, ..., 0.678]
```

---

### 阶段5：相似度计算

**核心算法**: 余弦相似度

```java
/**
 * 计算余弦相似度 - 向量检索的核心算法
 * cosine_similarity = (A · B) / (||A|| * ||B||)
 */
private double cosineSimilarity(float[] vectorA, float[] vectorB) {
    if (vectorA.length != vectorB.length) {
        throw new IllegalArgumentException("向量维度不匹配");
    }

    double dotProduct = 0.0;  // 点积
    double normA = 0.0;       // A的模
    double normB = 0.0;       // B的模

    for (int i = 0; i < vectorA.length; i++) {
        dotProduct += vectorA[i] * vectorB[i];
        normA += vectorA[i] * vectorA[i];
        normB += vectorB[i] * vectorB[i];
    }

    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

**数学原理**:
```
余弦相似度 = (A · B) / (||A|| × ||B||)

其中:
- A · B = Σ(A[i] × B[i])  (点积)
- ||A|| = √(ΣA[i]²)        (向量A的模/长度)
- ||B|| = √(ΣB[i]²)        (向量B的模/长度)

值域: [-1, 1]
-  1.0: 完全相似（方向相同）
-  0.0: 不相关（垂直）
- -1.0: 完全相反（方向相反）
```

**计算示例**:
```
查询向量: [0.2, 0.5, 0.8]
商品向量: [0.3, 0.4, 0.7]

点积 = 0.2×0.3 + 0.5×0.4 + 0.8×0.7 = 0.06 + 0.20 + 0.56 = 0.82
||A|| = √(0.2² + 0.5² + 0.8²) = √(0.04 + 0.25 + 0.64) = √0.93 ≈ 0.96
||B|| = √(0.3² + 0.4² + 0.7²) = √(0.09 + 0.16 + 0.49) = √0.74 ≈ 0.86

余弦相似度 = 0.82 / (0.96 × 0.86) ≈ 0.82 / 0.83 ≈ 0.99
```

---

### 阶段6：召回

```java
// 计算查询向量与所有商品向量的相似度
List<ProductWithScore> scoredProducts = new ArrayList<>();

for (Map.Entry<Integer, ProductWithEmbedding> entry : vectorIndex.entrySet()) {
    ProductWithEmbedding pwe = entry.getValue();
    double similarity = cosineSimilarity(queryEmbedding, pwe.embedding);
    scoredProducts.add(new ProductWithScore(pwe.product, similarity));
}

// 按相似度排序
scoredProducts.sort((a, b) -> Double.compare(b.score, a.score));

// 召回topK*2个候选（用于重排）
List<ProductWithScore> recalled = scoredProducts.stream()
    .limit(topK * 2)  // 召回更多候选
    .collect(Collectors.toList());

log.info("召回阶段：检索到{}个候选商品", recalled.size());
```

**召回策略**:
- 目标返回: 5个商品
- 实际召回: 10个商品 (topK × 2)
- 原因: 为后续重排提供更多候选

---

## 🎯 重排机制详解

### 重排的两个阶段

#### 阶段1: 向量相似度排序（基础重排）

```java
// 这是"基础重排规则"
scoredProducts.sort((a, b) -> Double.compare(b.score, a.score));
```

**基础规则**: 按照余弦相似度从高到低排序

#### 阶段2: 业务规则增强（自定义重排)

**文件**: `VectorRetrievalServiceImpl.java`

```java
private List<ProductWithScore> rerankProducts(List<ProductWithScore> recalled, String query, int topK) {
    log.info("开始重排，候选商品数：{}，目标topK：{}", recalled.size(), topK);

    return recalled.stream()
        .map(ps -> {
            double finalScore = ps.score;  // ← 基础分数（余弦相似度）

            // 自定义规则1：高评分商品加分
            if (ps.product.getRate() >= 9.0) {
                finalScore += 0.1;  // 加0.1分
            }

            // 自定义规则2：价格合理性加分
            if (ps.product.getPrice() >= 20 && ps.product.getPrice() <= 80) {
                finalScore += 0.05;  // 加0.05分
            }

            return new ProductWithScore(ps.product, finalScore);
        })
        .sorted((a, b) -> Double.compare(b.score, a.score))  // 重新排序
        .limit(topK)
        .collect(Collectors.toList());
}
```

### 重排示例

```
【召回阶段 - 基础排序（按余弦相似度）】
1. Java核心技术      相似度:0.85  评分:8.5  价格:120元
2. Effective Java    相似度:0.82  评分:9.8  价格:68元
3. Java编程思想      相似度:0.80  评分:9.5  价格:99元
4. Head First Java   相似度:0.78  评分:9.2  价格:55元
5. Java并发实战      相似度:0.75  评分:9.0  价格:89元

【重排阶段 - 应用自定义规则】
1. Effective Java    → 0.82 + 0.1(高评分) + 0.05(合理价格) = 0.97 ⭐
2. Java编程思想      → 0.80 + 0.1(高评分) = 0.90
3. Head First Java   → 0.78 + 0.1(高评分) + 0.05(合理价格) = 0.93
4. Java并发实战      → 0.75 + 0.1(高评分) + 0.05(合理价格) = 0.90
5. Java核心技术      → 0.85 + 0.0 = 0.85

【最终结果】
重排后顺序变化：
1. Effective Java    (0.97) ⭐ 从第2名升到第1名
2. Head First Java   (0.93)    从第4名升到第2名
3. Java编程思想      (0.90)    保持第3名
4. Java并发实战      (0.90)    从第5名升到第4名
5. Java核心技术      (0.85)    从第1名降到第5名
```

### 重排规则设计原则

1. **基础分数**: 向量相似度（反映语义相关性）
2. **评分加分**: 高评分商品更可靠
3. **价格加分**: 合理价格区间更受欢迎
4. **权重控制**: 加分幅度不宜过大（避免破坏语义相关性）

---

## 🔧 核心代码说明

### ChatService - RAG编排层

**文件**: `ChatServiceImpl.java`

```java
@Override
@Transactional(readOnly = true)
public String recommendBooks(String userQuery) {
    log.info("图书推荐查询: {}", userQuery);

    try {
        // ============ RAG第一步：检索 ============
        List<Product> retrievedProducts = vectorRetrievalService.similaritySearch(userQuery, 5);
        log.info("向量检索到{}个相关商品", retrievedProducts.size());

        // ============ RAG第二步：增强 ============
        String enhancedPrompt = buildRagPrompt(userQuery, retrievedProducts);

        // ============ RAG第三步：生成 ============
        String response = chatClientBuilder
            .build()
            .prompt()
            .user(enhancedPrompt)
            .call()
            .content();

        log.info("图书推荐回复: {}", response);
        return response;

    } catch (Exception e) {
        log.error("图书推荐失败", e);
        return "抱歉，推荐服务暂时不可用，请稍后再试";
    }
}
```

### Prompt增强实现

```java
private String buildRagPrompt(String userQuery, List<Product> retrievedProducts) {
    StringBuilder prompt = new StringBuilder();

    // 1. 设置AI角色
    prompt.append("你是一个专业的图书推荐助手");
    prompt.append("\n\n");

    // 2. 注入向量检索结果（这是RAG的关键！）
    prompt.append("【知识库检索结果】\n");
    prompt.append("基于用户的查询「").append(userQuery).append("」，");
    prompt.append("我通过向量相似度检索找到了以下相关图书：\n\n");

    for (int i = 0; i < retrievedProducts.size(); i++) {
        Product product = retrievedProducts.get(i);
        prompt.append(String.format(
            "%d. 《%s》\n   - 价格：￥%.2f\n   - 评分：%.1f分\n   - 分类：%s\n   - 简介：%s\n\n",
            i + 1,
            product.getTitle(),
            product.getPrice(),
            product.getRate(),
            product.getTag(),
            product.getDescription() != null ? product.getDescription() : "暂无简介"
        ));
    }

    prompt.append("【推荐要求】\n");
    prompt.append("请基于上述检索到的图书，为用户提供个性化的推荐建议。\n");
    prompt.append("推荐时请：\n");
    prompt.append("1. 重点推荐最相关的图书\n");
    prompt.append("2. 说明推荐理由\n");
    prompt.append("3. 考虑用户可能关心的因素（价格、评分、内容等）");

    return prompt.toString();
}
```

### 最终Prompt示例

```
你是一个专业的图书推荐助手

【知识库检索结果】
基于用户的查询「推荐一些Java编程书籍」，我通过向量相似度检索找到了以下相关图书：

1. Effective Java
   - 价格：￥68.00
   - 评分：9.8分
   - 分类：编程
   - 简介：Java必读经典，涵盖最佳实践

2. Head First Java
   - 价格：￥55.00
   - 评分：9.2分
   - 分类：编程
   - 简介：图文并茂的Java入门教程

3. Java编程思想
   - 价格：￥99.00
   - 评分：9.5分
   - 分类：编程
   - 简介：Java编程完整指南

【推荐要求】
请基于上述检索到的图书，为用户提供个性化的推荐建议。
推荐时请：
1. 重点推荐最相关的图书
2. 说明推荐理由
3. 考虑用户可能关心的因素（价格、评分、内容等）
```

---

## ⚙️ 配置说明

### application.yml配置

```yaml
spring:
  ai:
    # DeepSeek配置
    openai:
      api-key: ${DEEPSEEK_API_KEY:sk-your-key-here}
      base-url: https://api.deepseek.com/v1
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
          max-tokens: 2000

    # SimpleVectorStore配置（无需Docker，内存向量存储）
    vectorstore:
      simple:
        store-path: src/main/resources/data/vectorstore
        initialize-schema: true

aliyun:
  dashscope:
    api-key: "sk-your-dashscope-key-here"
```

### EmbeddingModel自动配置

Spring AI会根据application.yml中的配置自动创建`EmbeddingModel` bean：

```java
// 自动注入
private final EmbeddingModel embeddingModel;

// 使用
float[] embedding = embeddingModel.embed(text);
```

---

## 📊 数据结构说明

### ProductWithEmbedding

```java
private static class ProductWithEmbedding {
    Product product;      // 商品对象
    float[] embedding;    // 商品的向量表示（1024维）
}
```

### ProductWithScore

```java
private static class ProductWithScore {
    Product product;  // 商品对象
    double score;     // 相似度得分（-1.0到1.0）
}
```

### Document (Spring AI)

```java
public class Document {
    private String content;                // 文档内容（用于向量化）
    private Map<String, Object> metadata;   // 元数据（用于过滤和重排）
}
```

---

## 🎯 RAG实现的亮点

1. **双重实现**: 既有Spring AI标准实现，也有自定义实现
2. **重排策略**: 结合向量相似度和业务规则
3. **批处理优化**: 避免API限流（每次处理10个商品）
4. **内存缓存**: 向量索引常驻内存，查询速度快
5. **完整的RAG流程**: 从检索到生成一气呵成
6. **可扩展性**: 易于添加新的重排规则

---

## 🚀 使用示例

### API调用

```bash
# 简单对话
POST /api/chat/simple
{
  "message": "你好"
}

# 图书推荐（RAG）
POST /api/chat/recommend
{
  "query": "推荐一些Java编程书籍"
}
```

### 响应示例

```json
{
  "code": "200",
  "message": "推荐成功",
  "data": "根据您的需求，我为您推荐以下Java书籍：\n\n1. **Effective Java** - 这是一本..."
}
```

---

## 📝 总结

这个RAG实现遵循了标准的RAG流程：

1. **离线阶段**: 文档加载 → 向量化 → 存储
2. **在线阶段**: 查询向量化 → 相似度计算 → 召回 → 重排 → Prompt增强 → LLM生成

重排机制是亮点，结合了：
- **基础重排**: 余弦相似度（语义相关性）
- **自定义重排**: 业务规则（评分、价格等）

这种设计既保证了语义相关性，又融入了业务逻辑，是一个非常实用的RAG实现！
