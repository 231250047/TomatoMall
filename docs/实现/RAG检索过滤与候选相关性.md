# RAG 检索过滤与候选相关性

2026-09-17 阶段二实现。评测见[阶段二报告](../测试/2026-09-17-RAG阶段二修复验证.md)。查询改写、意图识别、多轮指代和自动提取约束留在 Agent 阶段，本次没有实现 RewriteQueryTransformer 或 CompressionQueryTransformer。

## 链路与职责

1. `ProductSearchQuery` 校验结构化参数，`ProductSnapshot.level()` 从规格“适用难度”读取统一值。
2. keyword/hybrid 模式先做完整书名匹配；书名存在但不符合条件则返回空，不替换成其他书。纯 vector 模式保留为对照，不走书名路由。
3. `PgProductVectorIndex` 返回 ID 和相似度：当前 Spring AI M4 的 distance 转成 `1-distance`，保留实际 cosine similarity。
4. 从 MySQL 商品快照校验价格、上下架、可售库存、分类、难度及排除项，再分路排序与 RRF 融合。
5. 推荐链路交给现有 LLM 选择至多 5 本；校验候选 ID、原文证据，返回前重新读取商品事实。模型失败返回暂不可用，不把候选直接当推荐。

## 检索工具参数

`POST /api/test/vectorstore/search`，仍需登录 token。例子：

```json
{
  "query": "Java 基础",
  "tag": "science",
  "minPrice": 0,
  "maxPrice": 80,
  "inStockOnly": true,
  "topK": 5,
  "mode": "hybrid",
  "level": "入门",
  "excludedTopics": ["源码"]
}
```

- 难度支持入门、进阶、高级；未传不限制。商品规格缺失、冲突或值不规范时，不满足显式难度要求。没有迁移数据库或自动补全所有商品规格。
- 分类仅接受已有分类枚举；金额非负且最多两位有效小数，最小值不能高于最大值。
- 排除项最多 10 个，每项 1–64 字符，去重后按商品文本匹配；英文完整词匹配避免 Java 误命中 JavaScript。
- 未支持的字段、错误类型、非法值返回参数错误，HTTP 400，避免调用者误以为过滤生效。
- 排除目前是字面过滤。例如“源码”也会命中“不讲源码”；不具备语义否定和同义词推理。自然语言中的“不要源码”不会自动转换成 excludedTopics。
- 上述是可被 Agent 复用的服务契约，还不是已经注册的 Agent Tool Calling。

## 两种候选使用方式

| 调用 | 规则 | 用途 |
| --- | --- | --- |
| `search` | 向量路默认最低相似度 0.54；关键词路保留词项匹配；RRF 取 topK | 普通检索与三策略评测 |
| `candidates` | 向量召回底线 0.30；聊天 topK=10，保留向量前 5 与关键词前 5，去重后按 RRF 补齐 | 给现有 LLM 进行相关性选择 |

0.54 仅根据 dev 集校准，并非通用最佳阈值。严格门槛减少误召回，也会漏掉自然语言相关书；因此选品候选采用较宽召回。关键词支持的商品仍可能出现低于 0.54 的相似度，不能把该值理解为整个混合结果的统一门槛。

RRF 排序分数 `score` 与 `vectorSimilarity` 分开返回：前者为各路 `1/(60+rank)` 之和，后者为余弦相似度，两者都不是“推荐正确概率”。完全书名结果是单路首位分数，向量相似度为空。

结果 `outcome` 区分 MATCHES、NO_MATCH、DEGRADED，内部推荐候选增加 CANDIDATES。向量失败即使关键词为空也标记 DEGRADED，不能把服务故障视作确定无匹配。MATCHES 仅表示检索命中，不保证语义全部相关。

## 生成上下文修正

向量文档继续只保存稳定商品文本。传给 LLM 的上下文额外带 MySQL 校验后的 `price`、`availableStock`，让模型可以核验预算。此前 q32 检索已找到 79.99 元商品，但上下文不含价格，模型拒绝选择。

选品的 `chatWithPrompt` 输出预算由 2000 调至 4096 token，普通 `chat` 保持 2000，HTTP 读取超时由 15 秒调至 30 秒；模型仍为配置的 deepseek-v4-flash。原因是实际出现 reasoningTokens=2000、finishReason=length、正文为空。曾试验 [官方支持的非思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)，但本批选品输出校验失败增加，已撤回该开关，完整保存失败结果。最终保留模型默认思考行为，不增加自动重试；成本上限与最坏等待时间相应增加。

## 当前限制

关键词仍是英文词与中文二元片段，不是 BM25；全量商品快照过滤适合当前小商品库，尚无规模性能结论。排除偏好、冗长输入、难度意图仍需 Agent 解析为结构化参数。新题只代表合成数据回归，不能视为真实用户全面验收。
