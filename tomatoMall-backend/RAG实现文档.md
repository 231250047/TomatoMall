# RAG智能图书推荐系统 - 完整实现文档

## 📋 目录

- [1. 系统概述](#1-系统概述)
- [2. RAG核心概念](#2-rag核心概念)
- [3. 系统架构](#3-系统架构)
- [4. Prompt驱动设计](#4-prompt驱动设计)
- [5. 技术实现](#5-技术实现)
- [6. 配置指南](#6-配置指南)
- [7. 使用示例](#7-使用示例)
- [8. 优化与扩展](#8-优化与扩展)

---

## 1. 系统概述

### 1.1 项目背景

本项目是一个基于**RAG（Retrieval-Augmented Generation）**架构的智能图书推荐系统，集成到番茄商城项目中。

### 1.2 核心功能

- ✅ **智能对话**：支持日常聊天和图书推荐
- ✅ **语义检索**：基于向量相似度检索相关书籍
- ✅ **智能判断**：LLM自主判断是否使用RAG
- ✅ **个性化推荐**：结合评分、价格等多维度排序

### 1.3 技术栈

```
┌─────────────────────────────────────────────────────────────┐
│                    技术架构                                     │
├─────────────────────────────────────────────────────────────┤
│ 后端框架 │ Spring Boot 3.x + Spring AI 1.0.0-M4                │
│ 嵌入模型   │ 阿里云DashScope text-embedding-v4 (1024维)          │
│ 对话模型   │ DeepSeek Chat API (deepseek-chat)                 │
│ 向量存储   │ 内存HashMap（自定义实现）                            │
│ 数据库     │ MySQL（商品数据存储）                              │
│ 开发语言   │ Java 17                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. RAG核心概念

### 2.1 什么是RAG？

**RAG (Retrieval-Augmented Generation)** 是一种增强大语言模型能力的技术架构：

```
┌─────────────────────────────────────────────────────────────┐
│              传统LLM vs RAG架构对比                           │
└─────────────────────────────────────────────────────────────┘

【传统LLM】
用户查询 → LLM直接生成回复
问题：LLM知识有限，可能产生幻觉

【RAG架构】
用户查询 → 检索知识库 → LLM基于检索结果生成回复
优势：答案基于真实数据，减少幻觉
```

### 2.2 RAG的三个阶段

```
┌─────────────────────────────────────────────────────────────┐
│                   RAG三阶段                                    │
└─────────────────────────────────────────────────────────────┘

阶段1: Retrieval (检索)
  ├─ 文档向量化（Embedding）
  ├─ 向量存储（Vector Store）
  └─ 相似度计算（Cosine Similarity）

阶段2: Augmentation (增强)
  ├─ 召回（Recall）：获取topK个候选文档
  ├─ 重排（Rerank）：应用业务规则重新排序
  └─ Prompt工程：构建增强的提示词

阶段3: Generation (生成)
  └─ LLM生成：基于检索结果生成回复
```

### 2.3 为什么使用RAG？

| 传统聊天 | RAG聊天 |
|---------|---------|
| ❌ 可能胡编书籍信息 | ✅ 基于真实数据库 |
| ❌ 不知道库存状态 | ✅ 实时数据支持 |
| ❌ 无法个性化推荐 | ✅ 多维度智能推荐 |
| ❌ 知识截止日期限制 | ✅ 可随时更新知识库 |

---

## 3. 系统架构

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                   系统整体架构                                 │
└─────────────────────────────────────────────────────────────┘

┌──────────────┐
│   前端       │  Vue.js + Axios
└──────┬───────┘
       │ HTTP POST
       ↓
┌─────────────────────────────────────────────────────────────┐
│              ChatController (REST API)                    │
│  /api/chat/simple  →  简单对话                               │
│  /api/chat/recommend → 图书推荐                             │
└──────┬──────────────────────────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────────────────────────┐
│          ChatService (业务编排层)                         │
│  ┌───────────────────────────────────────────────────┐      │
│  │ buildSmartPrompt() - 构建智能Prompt               │      │
│  │  ├─ 加载知识库（向量检索）                          │      │
│  │  └─ 让LLM自主判断是否使用RAG                       │      │
│  └───────────────────────────────────────────────────┘      │
└──────┬──────────────────────────────────────────────────────┘
       │
       ├──────────────┬──────────────┐
       ↓              ↓              ↓
┌─────────────┐  ┌─────────────┐  ┌──────────────┐
│VectorStore  │  │DeepSeek     │  │DashScope    │
│向量检索服务  │  │Chat服务     │  │Embedding    │
│              │  │             │  │服务         │
│余弦相似度    │  │HTTP调用     │  │text-        │
│计算+重排     │  │             │  │embedding-v4 │
└──────┬───────┘  └───────────┬───┘  └──────────────┘
       │                  │
       │                  ↓
       │         ┌─────────────┐
       │         │   MySQL     │
       │         │  商品数据库  │
       │         └─────────────┘
       │
       ↓
┌─────────────────────────────────────────────────────────────┐
│                   返回用户推荐结果                               │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心模块说明

| 模块 | 类名 | 功能 |
|------|------|------|
| **向量检索** | `VectorRetrievalServiceImpl` | 向量存储、相似度计算、召回重排 |
| **Embedding** | `DashScopeEmbeddingModelConfig` | 文本向量化（阿里云） |
| **对话生成** | `DeepSeekServiceImpl` | LLM调用（DeepSeek API） |
| **业务编排** | `ChatServiceImpl` | Prompt构建、流程协调 |

---

## 4. Prompt驱动设计

### 4.1 设计理念

**核心理念**：让LLM自己判断是否需要使用知识库，而不是硬编码规则。

```
┌─────────────────────────────────────────────────────────────┐
│            硬编码 vs Prompt驱动对比                          │
└─────────────────────────────────────────────────────────────┘

【硬编码方案】
程序员定义规则：
if (message.contains("推荐") && message.contains("书")) {
    return useRAG();
} else {
    return directChat();
}

问题：
❌ 需要穷举所有表达方式
❌ 无法理解复杂语义
❌ 维护成本高

【Prompt驱动方案】✅
在Prompt中说明判断规则：
"你是一个智能助手。如果用户询问图书推荐，请使用知识库。
 如果用户只是打招呼，请直接回复。"

优势：
✅ LLM理解各种表达
✅ 灵活适应新场景
✅ 只需修改Prompt即可
```

### 4.2 智能Prompt设计

**完整Prompt结构**：

```markdown
你是一个智能图书推荐助手，具备以下能力：
- 可以进行友好的对话交流
- 可以根据知识库推荐相关书籍
- 能够智能判断用户意图

【知识库】以下是系统检索到的相关书籍（供推荐时使用）：

1. 《Effective Java》
   - 价格: ￥89.00
   - 评分: 9.7分
   - 分类: 编程
   - 简介: Java必读经典...

【回复要求】请根据用户消息智能判断并回复：

1️⃣ 如果用户**询问图书推荐**（如"推荐Java书"、"有什么Python入门书"）：
   - 请从上述知识库中选择最相关的书籍进行推荐
   - 重点推荐评分高、与用户需求匹配的书籍
   - 说明推荐理由
   - 不要提及不相关的书籍

2️⃣ 如果用户**只是打招呼或闲聊**（如"你好"、"在吗"、"今天天气怎么样"）：
   - 请友好地回复，不要提及知识库中的书籍
   - 进行自然的对话交流

3️⃣ 如果用户**询问其他问题**：
   - 尽力回答
   - 如果不知道，礼貌地说明

────────────────────────────
【用户消息】
推荐Java学习书籍
────────────────────────────

请开始回复：
```

### 4.3 Prompt设计原则

1. **明确角色定位**
   ```markdown
   你是一个智能图书推荐助手，具备以下能力：
   - 可以进行友好的对话交流
   - 可以根据知识库推荐相关书籍
   - 能够智能判断用户意图
   ```

2. **提供上下文**
   ```markdown
   【知识库】以下是系统检索到的相关书籍：
   ```

3. **清晰的判断规则**
   ```markdown
   【回复要求】请根据用户消息智能判断并回复：
   1️⃣ 如果... → ...
   2️⃣ 如果... → ...
   3️⃣ 如果... → ...
   ```

4. **边界情况处理**
   ```markdown
   - 不要提及不相关的书籍
   - 不要说"其他书籍不推荐"之类的话
   ```

---

## 5. 技术实现

### 5.1 向量检索服务

**文件**: `VectorRetrievalServiceImpl.java`

#### 5.1.1 数据结构

```java
// 商品 + 向量表示
private static class ProductWithEmbedding {
    Product product;
    float[] embedding;  // 1024维向量
}

// 内存中的向量索引
private Map<Integer, ProductWithEmbedding> vectorIndex = new HashMap<>();
```

#### 5.1.2 向量索引构建

```java
@Override
@Transactional(readOnly = true)
public void buildVectorIndex() {
    // 1. 加载所有商品
    List<Product> products = productRepository.findAll();

    // 2. 批量向量化（每批10个，避免API限流）
    int batchSize = 10;
    for (int i = 0; i < products.size(); i += batchSize) {
        List<Product> batch = products.subList(i, Math.min(i + batchSize, products.size()));

        for (Product product : batch) {
            // 商品转换为文本
            String text = productToText(product);
            // 调用阿里云Embedding API
            float[] embedding = embeddingModel.embed(text);
            // 存入内存索引
            vectorIndex.put(product.getId(), new ProductWithEmbedding(product, embedding));
        }
    }
}
```

**商品文本格式**：
```java
书名:Effective Java
价格:￥89.00
评分:9.7
分类:education
简介:帮助初学者...
```

#### 5.1.3 向量检索

```java
@Override
public List<Product> similaritySearch(String query, int topK) {
    // 1. 查询向量化
    float[] queryEmbedding = embedQuery(query);

    // 2. 计算与所有商品的相似度
    List<ProductWithScore> scoredProducts = new ArrayList<>();
    for (Map.Entry<Integer, ProductWithEmbedding> entry : vectorIndex.entrySet()) {
        double similarity = cosineSimilarity(queryEmbedding, entry.getValue().embedding);
        scoredProducts.add(new ProductWithScore(entry.getValue().product, similarity));
    }

    // 3. 按相似度排序
    scoredProducts.sort((a, b) -> Double.compare(b.score, a.score));

    // 4. 召回topK*2个候选（用于重排）
    List<ProductWithScore> recalled = scoredProducts.stream()
        .limit(topK * 2)
        .collect(Collectors.toList());

    // 5. 重排（应用业务规则）
    return rerankProducts(recalled, query, topK);
}
```

#### 5.1.4 相似度计算

```java
private double cosineSimilarity(float[] vectorA, float[] vectorB) {
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

**数学公式**：
```
余弦相似度 = (A · B) / (||A|| × ||B||)

其中：
- A · B = Σ(A[i] × B[i])
- ||A|| = √(ΣA[i]²)
- ||B|| = √(ΣB[i]²)

值域: [-1, 1]
-  1.0: 完全相似
-  0.0: 不相关
- -1.0: 完全相反
```

#### 5.1.5 重排机制

```java
private List<ProductWithScore> rerankProducts(List<ProductWithScore> recalled, String query, int topK) {
    return recalled.stream()
        .map(ps -> {
            double finalScore = ps.score;  // 基础分数（余弦相似度）

            // 重排规则1: 高评分商品加分
            if (ps.product.getRate() >= 9.0) {
                finalScore += 0.1;
            }

            // 重排规则2: 价格合理加分
            if (ps.product.getPrice().compareTo(new BigDecimal("20.0")) >= 0 &&
                ps.product.getPrice().compareTo(new BigDecimal("80.0")) <= 0) {
                finalScore += 0.05;
            }

            return new ProductWithScore(ps.product, finalScore);
        })
        .sorted((a, b) -> Double.compare(b.score, a.score))  // 重新排序
        .limit(topK)
        .collect(Collectors.toList());
}
```

**重排示例**：
```
召回阶段（按相似度）：
1. Effective Java    相似度:0.82  评分:9.7  价格:￥89
2. Java编程思想      相似度:0.80  评分:9.5  价格:￥99
3. Head First Java   相似度:0.78  评分:9.2  价格:￥55

重排后（结合业务规则）：
1. Effective Java    0.82 + 0.1(高评分) = 0.92 ⭐
2. Head First Java   0.78 + 0.1(高评分) + 0.05(合理价) = 0.93
3. Java编程思想      0.80 + 0.1(高评分) = 0.90
```

---

### 5.2 Embedding服务

**文件**: `DashScopeEmbeddingModelConfig.java`

#### 5.2.1 配置类

```java
@Configuration
@Component
public class DashScopeEmbeddingModelConfig {

    @Value("${aliyun.dashscope.api-key}")
    private String apiKey;

    @Value("${aliyun.dashscope.embedding.model:text-embedding-v4}")
    private String model;

    @Value("${aliyun.dashscope.embedding.dimensions:1024}")
    private int dimensions;

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║           🔧 初始化阿里云DashScope Embedding模型               ║");
        log.info("╚════════════════════════════════════════════════════════════╝");
        log.info("📌 API Key: {}", maskApiKey(apiKey));
        log.info("📌 模型: {}", model);
        log.info("📌 向量维度: {}", dimensions);

        TextEmbedding textEmbedding = new TextEmbedding();
        return new DashScopeEmbeddingModel(textEmbedding, model, dimensions, apiKey);
    }
}
```

#### 5.2.2 Embedding模型实现

```java
@Slf4j
class DashScopeEmbeddingModel implements EmbeddingModel {

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // 获取输入文本
        List<String> inputs = request.getInstructions();

        // 调用阿里云API
        for (int i = 0; i < inputs.size(); i++) {
            String text = inputs.get(i);

            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(model)
                    .text(text)
                    .apiKey(apiKey)
                    .build();

            TextEmbeddingResult result = textEmbedding.call(param);
            // 提取向量并转换...
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(String text) {
        TextEmbeddingParam param = TextEmbeddingParam.builder()
                .model(model)
                .text(text)
                .apiKey(apiKey)
                .build();

        TextEmbeddingResult result = textEmbedding.call(param);
        return extractEmbedding(result);
    }
}
```

---

### 5.3 Chat服务（Prompt驱动核心）

**文件**: `ChatServiceImpl.java`

#### 5.3.1 主流程

```java
@Override
@Transactional(readOnly = true)
public String chat(String userMessage) {
    // 1. 先进行向量检索（获取知识库）
    List<Product> knowledgeBase = vectorRetrievalService.similaritySearch(userMessage, 3);

    // 2. 构建智能Prompt，让LLM自己决定
    String smartPrompt = buildSmartPrompt(userMessage, knowledgeBase);

    // 3. 调用DeepSeek
    String response = deepSeekService.chatWithPrompt(smartPrompt);

    return response;
}
```

#### 5.3.2 智能Prompt构建

```java
private String buildSmartPrompt(String userMessage, List<Product> knowledgeBase) {
    StringBuilder prompt = new StringBuilder();

    // 1. 设置角色
    prompt.append("你是一个智能图书推荐助手，具备以下能力：\n");
    prompt.append("- 可以进行友好的对话交流\n");
    prompt.append("- 可以根据知识库推荐相关书籍\n");
    prompt.append("- 能够智能判断用户意图\n\n");

    // 2. 提供知识库
    if (!knowledgeBase.isEmpty()) {
        prompt.append("【知识库】以下是系统检索到的相关书籍（供推荐时使用）：\n\n");
        for (Product product : knowledgeBase) {
            prompt.append(String.format(
                "%d. 《%s》\n   - 价格: ￥%.2f\n   - 评分: %.1f分\n   - 分类: %s\n   - 简介: %s\n\n",
                i + 1, product.getTitle(), product.getPrice(),
                product.getRate(), product.getTag(),
                truncate(product.getDescription(), 80)
            ));
        }
    }

    // 3. 核心指令：让LLM智能判断
    prompt.append("【回复要求】请根据用户消息智能判断并回复：\n\n");
    prompt.append("1️⃣ 如果用户**询问图书推荐**（如\"推荐Java书\"、\"有什么Python入门书\"）：\n");
    prompt.append("   - 请从上述知识库中选择最相关的书籍进行推荐\n");
    prompt.append("   - 重点推荐评分高、与用户需求匹配的书籍\n");
    prompt.append("   - 说明推荐理由\n");
    prompt.append("   - 不要提及不相关的书籍\n\n");

    prompt.append("2️⃣ 如果用户**只是打招呼或闲聊**（如\"你好\"、\"在吗\"、\"今天天气怎么样\"）：\n");
    prompt.append("   - 请友好地回复，不要提及知识库中的书籍\n");
    prompt.append("   - 进行自然的对话交流\n\n");

    prompt.append("3️⃣ 如果用户**询问其他问题**：\n");
    prompt.append("   - 尽力回答\n");
    prompt.append("   - 如果不知道，礼貌地说明\n\n");

    // 4. 用户消息
    prompt.append("────────────────────────────\n");
    prompt.append("【用户消息】\n");
    prompt.append(userMessage);
    prompt.append("\n────────────────────────────\n\n");

    prompt.append("请开始回复：");

    return prompt.toString();
}
```

---

### 5.4 DeepSeek API调用服务

**文件**: `DeepSeekServiceImpl.java`

#### 5.4.1 HTTP调用

```java
private String sendRequest(ChatRequest request) throws Exception {
    // 1. 序列化请求
    String requestJson = objectMapper.writeValueAsString(request);

    // 2. 设置请求头
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    // 3. 发送HTTP请求
    ResponseEntity<String> response = restTemplate.exchange(
        "https://api.deepseek.com/v1/chat/completions",
        HttpMethod.POST,
        new HttpEntity<>(requestJson, headers),
        String.class
    );

    // 4. 返回响应
    return response.getBody();
}
```

**请求格式**：
```json
{
  "model": "deepseek-chat",
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "max_tokens": 2000,
  "temperature": 0.7
}
```

#### 5.4.2 响应解析

```java
@Data
private static class ChatResponse {
    String id;
    String object;
    Long created;
    String model;
    List<Choice> choices;
    Usage usage;
}

@Data
private static class Choice {
    Integer index;
    Message message;
    String finish_reason;
    Object logprobs;
}

@Data
private static class Message {
    String role;
    String content;
}
```

---

## 6. 配置指南

### 6.1 application.yml配置

```yaml
spring:
  # Spring AI 配置
  ai:
    # DeepSeek配置
    openai:
      api-key: ${DEEPSEEK_API_KEY:sk-your-deepseek-key}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
          max-tokens: 2000

    # SimpleVectorStore配置（内存向量存储）
    vectorstore:
      simple:
        store-path: src/main/resources/data/vectorstore
        initialize-schema: true

# 阿里云DashScope配置
aliyun:
  dashscope:
    api-key: "sk-your-dashscope-key"

# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/tomatomall?characterEncoding=utf-8
    username: root
    password: your-password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 6.2 API Key获取

#### DeepSeek API Key
1. 访问：https://platform.deepseek.com/
2. 注册/登录
3. 创建API Key
4. 复制Key到 `application.yml`

#### 阿里云DashScope API Key
1. 访问：https://dashscope.aliyun.com/
2. 开通文字 embedding服务
3. 创建API Key
4. 复制Key到 `application.yml`

---

## 7. 使用示例

### 7.1 API接口

#### 简单对话
```bash
curl -X POST http://localhost:8080/api/chat/simple \
  -H "Content-Type: application/json" \
  -d '{
    "message": "你好"
  }'
```

**响应**：
```json
{
  "code": "200",
  "message": "对话成功",
  "data": "你好！很高兴见到你。有什么我可以帮助你的吗？"
}
```

#### 图书推荐
```bash
curl -X POST http://localhost:8080/api/chat/simple \
  -H "Content-Type: application/json" \
  -d '{
    "message": "推荐一些Java学习书籍"
  }'
```

**响应**：
```json
{
  "code": "200",
  "message": "对话成功",
  "data": "根据您的需求，我推荐以下Java学习书籍：\n\n1. **《Effective Java》**\n   - 评分：9.7分\n   - 价格：￥89.00\n   - 推荐理由：这是一本Java必读经典..."
}
```

### 7.2 日志示例

#### 简单打招呼日志
```
╔════════════════════════════════════════════════════════════╗
║           💬 AI对话（LLM智能判断模式）                       ║
╚════════════════════════════════════════════════════════════╝
📝 用户消息: 你好
🤖 让LLM判断用户意图...
📚 已加载知识库（3本书）

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📤 发送HTTP请求到DeepSeek...
🔗 目标URL: https://api.deepseek.com/v1/chat/completions
🔑 API Key: sk-a409...3bf3
📥 HTTP状态码: 200 OK
✅ DeepSeek API调用成功
✅ LLM智能判断完成
╚════════════════════════════════════════════════════════════╝
```

#### 图书推荐日志
```
╔════════════════════════════════════════════════════════════╗
║           💬 AI对话（LLM智能判断模式）                       ║
╚════════════════════════════════════════════════════════════╝
📝 用户消息: 推荐一些Java学习书籍
🤖 让LLM判断用户意图...
📚 已加载知识库（3本书）

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 RAG第一步:向量检索（召回 + 重排）
⏳ 开始调用向量检索服务...
✅ 向量检索完成，检索到 3 个相关商品
📖 商品[1]: Effective Java (评分: 9.7, 价格: ¥89.00)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 RAG第二步:构建增强Prompt
⏳ 将检索结果注入到Prompt中...
✅ Prompt构建完成，长度: 856 字符

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 RAG第三步:调用DeepSeek生成推荐
⏳ 开始调用DeepSeek Chat API...
✅ DeepSeek API调用成功
✅ LLM智能判断完成
╚════════════════════════════════════════════════════════════╝
```

---

## 8. 优化与扩展

### 8.1 性能优化

#### 向量索引缓存
```java
// 应用启动时构建，避免每次查询都构建
@PostConstruct
public void init() {
    if (vectorIndex.isEmpty()) {
        buildVectorIndex();
    }
}
```

#### 批处理优化
```java
// 批量向量化，减少API调用次数
int batchSize = 10;  // 每次处理10个
```

### 8.2 准确性优化

#### 提高向量维度
```yaml
aliyun:
  dashscope:
    embedding:
      model: text-embedding-v4  # 1024维 → 更精确
      dimensions: 1024
```

#### 调整topK数量
```java
List<Product> knowledgeBase = vectorRetrievalService.similaritySearch(userQuery, 3);
// 根据实际需求调整：3-5个最相关
```

### 8.3 扩展方向

#### 1. 添加更多重排规则
```java
// 库存充裕加分
if (hasGoodStock(ps.product.getId())) {
    finalScore += 0.03;
}

// 新书加分
if (isNewArrival(ps.getPublishDate())) {
    finalScore += 0.02;
}
```

#### 2. 多轮对话支持
```java
// 维护对话历史
List<Message> conversationHistory = new ArrayList<>();
conversationHistory.add(new Message("user", userMessage));
conversationHistory.add(new Message("assistant", previousResponse));

// 传递给LLM
messages.addAll(conversationHistory);
messages.add(new Message("user", userMessage));
```

#### 3. A/B测试
```java
// 测试不同Prompt版本
String responseA = chatWithPromptV1(userMessage);
String responseB = chatWithPromptV2(userMessage);

// 收集用户反馈，选择更好的版本
```

---

## 📚 附录

### A. 依赖版本

| 依赖 | 版本 |
|------|------|
| Spring AI | 1.0.0-M4 |
| DashScope SDK | 2.16.7 |
| Spring Boot | 3.x |
| Java | 17 |

### B. 相关文档

- `RAG优化方案.md` - RAG系统优化历史
- `架构演进-从硬编码到Prompt驱动.md` - 架构设计思路
- `调试指南.md` - 问题排查指南
- `完整测试指南.md` - 测试流程

### C. 常见问题

**Q1: 向量检索为什么返回空结果？**
- 检查向量索引是否已构建
- 检查查询是否与商品描述相关
- 调整相似度阈值

**Q2: DeepSeek API调用失败？**
- 检查API Key是否有效
- 检查网络连接
- 查看错误日志

**Q3: 如何更新知识库？**
- 添加新商品到MySQL
- 重启应用（触发`@PostConstruct`）
- 或手动调用`buildVectorIndex()`

---

**版本**: v6.0 - Prompt驱动架构
**更新时间**: 2026-04-23
**维护者**: Claude + 用户
**许可证**: MIT
