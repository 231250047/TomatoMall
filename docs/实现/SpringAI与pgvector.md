# Spring AI 与 pgvector 商品检索实现

## 1. 范围和代码入口

本次将聊天与原 `/api/test/vectorstore/search` 统一到 `ProductRetrievalService`。删除原 `RagService`、`VectorRetrievalService` 及各自实现，移除内存 HashMap 和 SimpleVectorStore 双链路。保留 Spring AI 1.0.0-M4，新增同版本 `spring-ai-pgvector-store` 与 PostgreSQL 驱动，未升级交易框架。

| 文件（相对后端 src/main/java/com/example/tomatomall） | 职责 |
| --- | --- |
| retrieval/ProductCatalog.java | 短 MySQL 只读事务读取商品、库存与规格，转换为脱离 JPA 的快照 |
| retrieval/ProductDocuments.java | 一商品一文档、稳定 UUID、内容 SHA-256、文本版本 |
| retrieval/ProductRetrievalService.java | 关键词与向量召回、RRF 融合、当前业务条件过滤、降级标记 |
| retrieval/PgProductVectorIndex.java | Spring AI PgVectorStore、独立 PG 连接池、配置绑定、持久化与索引更新互斥 |
| retrieval/ProductIndexMaintenance.java | 独立后台线程核对全量商品与遗留索引，状态与进度 |
| retrieval/RocketMqProductTransport.java | 普通商品事件发送、消费、失败重投 |
| service/serviceImpl/ChatServiceImpl.java | 共用检索，验证模型选择的 ID/原文证据，生成后重新核对 MySQL |
| controller/VectorStoreTestController.java | 沿用旧 URL；真实索引状态、受管理员保护的后台构建、共享检索 |

## 2. 存储与一致性

MySQL 保存事实，PostgreSQL 保存可重建派生索引。PG 连接池在适配器内部管理，不注册成 Spring DataSource/事务管理器 bean，防止替换商城 JPA 的 MySQL 数据源。

商品短文本包含书名、分类、简介、详情和排序后的规格；不把动态价格、库存放入 Embedding。商品 ID 通过 `UUID.nameUUIDFromBytes("product:" + id)` 转换，符合 M4 pgvector 表的 UUID 主键要求。元数据包含 productId、contentHash、textVersion。

`rag_index_config` 保存 namespace/model/dimensions/textVersion 指纹。无配置但已有向量时拒绝自动接管，防止不同模型的同维度向量混用。模型或维度改变时创建新向量库、按新维度建表、离线补齐后切换连接并重启应用；不要删除配置行强行绕过校验。

当前使用精确余弦搜索；未创建 HNSW。PostgreSQL 数据卷提供持久化，重启不重新 Embedding 已存在且摘要相同的商品。备份仍需 pg_dump/卷备份，持久化不等于备份和高可用。

## 3. 商品变化链路

```text
商品新增/修改/下架、规格新增/修改
→ 同一个 MySQL 事务写 PRODUCT_CHANGED Outbox
→ Outbox worker 发送 NORMAL Topic
→ consumer 调用 index.sync(productId)
→ PostgreSQL 提交完成
→ 消费 SUCCESS
```

- 消息只带协议 version、eventId、productId。协议 version 不是商品版本。
- `Outbox.sent=true` 只代表发送被 broker 确认，不代表索引已经更新。
- 规格迁移到另一商品时给旧、新商品分别写事件。
- PG writer 先取得数据库级事务 advisory lock，再短事务读取 MySQL 最新状态。并发实例、周期核对与 MQ 使用同一个锁；旧消息不会携带旧正文覆盖新索引。
- 写入后再次读取 MySQL；生成期间发生文本变化/下架则回滚 PG，失败重试。
- 已存在相同内容摘要则跳过 Embedding；下架或删除则移除稳定文档 ID。
- 这是最终一致；写入前后校验仍不能让跨库瞬时强一致。检索后、模型生成后都会核对当前业务事实。
- 全局写锁适合目前100件级别目录，吞吐有明确限制。后续放大规模应改为按商品互斥、分批核对，并重新测试竞态。
- MQ 禁用/断连时事件保留并退避，不能宣称同步成功。消费超出 broker 重试上限可能进 DLQ，需监控重放；5分钟核对补偿索引漂移。

核对不会先清空在线索引，也不会整体替换一个旧快照；逐商品调用同一 sync。后台线程与订单定时扫描隔离，首次30秒后触发，之后每5分钟触发。构建接口提交任务后返回 ACCEPTED；正在执行时返回 ALREADY_RUNNING，通过 status 查看进度。任务中断后下一次重新核对，已同步摘要跳过。

## 4. 检索与回答

- 两路候选最多各200个，关键词是英文字词/中文双字匹配的小目录基线，不是 BM25。
- RRF 用 `1/(60+rank)` 融合去重；移除原先根据评分/价格字符串随意加分的规则。
- 向量调用之后再读取 MySQL，严格筛选 available、amount-frozen>0、分类和价格；结果不足如实返回，不用不符合条件的商品补齐。
- 支持单本“不超过80元”等明确预算；“低于80元”按商品两位小数精度转换为79.99元上限，“不低于80元”对应下限；组合总预算返回澄清提示。尚不是通用自然语言条件解析器，复杂条件优先传结构化参数。
- 向量异常返回 `strategy=keyword, degraded=true, reason=VECTOR_UNAVAILABLE`；无结果与降级可区分。`mode=keyword` 用于基线；`mode=vector` 遇到故障仍明确降级，评测时必须排除/单列降级结果。
- 模型只选择候选 ID 和原文摘录；后端验证整数范围、候选归属及引用子串。合法空选品返回无匹配；无效输出/模型失败提示推荐暂不可用，不再回填候选。见 [阶段一修复](RAG无匹配与模型异常处理.md)。
- 返回前再次读取商品，确认未下架/售罄/超预算，标题和价格由 MySQL 提供；变化后的旧证据不展示。
- 这保证引用来源和商品事实约束，不证明“最适合用户”；排序相关性仍需真实模型评测。

## 5. 运行配置

独立 Compose：`docker compose -f compose.rag.yml up -d`，先设置本机环境变量 RAG_PG_PASSWORD。Docker 首次初始化执行 `infra/rag/init.sql`，默认 vector(1024)。已有卷不会重新执行初始化脚本。

已有 Spring Boot 配置中追加：

```properties
rag.vector.enabled=true
rag.vector.url=jdbc:postgresql://127.0.0.1:15432/tomatomall_vectors
rag.vector.username=tomatomall
rag.vector.password=本机配置
rag.vector.namespace=tomatomall_demo
aliyun.dashscope.embedding.model=text-embedding-v4
aliyun.dashscope.embedding.dimensions=1024
aliyun.dashscope.api-key=通过本机私有配置或环境变量提供
rag.mq.enabled=true
rag.mq.topic=product-changed
rag.mq.consumer-group=product-index-consumer
orders.mq.endpoints=localhost:18081
spring.jpa.open-in-view=false
```

已有订单库先应用 `tomatoMall-backend/db/migration/V2_product_index_events.sql`，确保kind字段可存PRODUCT_CHANGED。原V1已使用VARCHAR；V2也兼容历史Hibernate生成的ENUM。

生产/共享环境请使用密码、最小权限和受限网络；不要沿用本机隔离实例的 trust 配置。每个 MySQL 数据集对应独立向量数据库与 namespace，不能将 demo/eval 商品ID空间混用。

现有 RocketMQ 5.x 需预建 NORMAL topic `product-changed` 和 group `product-index-consumer`。不要复用订单 DELAY topic。命令中的 nameserver/broker 地址按环境替换：

```sh
bin/mqadmin updateTopic -n localhost:19876 -c DefaultCluster -t product-changed -a +message.type=NORMAL
bin/mqadmin updateSubGroup -n localhost:19876 -c DefaultCluster -g product-index-consumer
```

M4 是项目现有 milestone 版本，最新文档中的 builder/API 不一定匹配；本次按已安装 JAR 和 M4 API 编译验证。后续升级应单独做兼容回归。

## 6. 接口

所有接口沿用现有 token 登录拦截。

- `POST /api/test/vectorstore/build`：管理员触发后台核对。
- `GET /api/test/vectorstore/status`：管理员读取真实连接/配置状态及最近核对进度。
- `POST /api/test/vectorstore/search`：`{"query":"Java项目实践","topK":5,"maxPrice":80,"inStockOnly":true,"mode":"hybrid"}`，可传 tag/minPrice；返回 items/strategy/degraded/reason/elapsedMs。
- `POST /api/chat/simple`：保持原有前端字符串协议，内部使用统一检索与受控输出。商品卡片和工具调用交互留在阶段C。

索引状态 UP 只说明数据库及指纹检查通过，不说明模型网络健康、索引完全追平或推荐质量达标。核对结果单列显示。

## 7. 规模与验证边界

当前关键词基线读取完整小目录，规格读取也未做批量优化。没有大规模性能测试，不宣称高并发检索或百万商品规模。候选上限、固定0.3向量阈值、固定RRF参数均需未来真实评测后调整。

完整结果见 [验证记录](../测试/RAG验证记录.md)，测试数据见 [测试数据与评测](../测试/RAG测试数据与评测.md)。

## 权限补充

新索引管理接口依赖管理员角色。本次修正公开注册接受admin以及资料修改提升role两个入口：公开注册固定user，自助资料不修改role；管理员由受控后台或数据库初始化分配。两项回归先复现失败后修正通过。

## 阶段二更新

检索阈值、结构化难度与排除过滤、推荐候选池及事实上下文以 [阶段二实现](RAG检索过滤与候选相关性.md) 为准。
