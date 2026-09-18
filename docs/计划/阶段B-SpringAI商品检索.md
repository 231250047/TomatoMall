# Spring AI 商品检索实施计划

> 执行方式：使用 writing-plans、executing-plans 和 test-driven-development，按任务验证；沿用当前工作区已有订单改造，不提交其他未提交内容。

## 已确认设计

目标：将聊天和调试入口统一为商品检索服务。MySQL 为商品事实来源，Spring AI PgVectorStore 保存派生向量；关键词与向量使用 RRF 融合，返回前校验当前价格、上下架和可售库存。Outbox + 普通 RocketMQ 事件驱动同步，定期核对补偿。缺少向量服务时提供明确标记的关键词降级。

技术边界：Java 17、Spring Boot 3.2.4、现有 Spring AI 版本先验证兼容性，不连带升级订单栈。PostgreSQL 使用独立连接，不替换 JPA/MySQL DataSource。商品短文档一商品一条；模型/维度/文本版本必须隔离。初期精确向量搜索，不声明 HNSW 性能收益。

## 任务与验收

- [x] 1. 核对依赖与基线：新增 pgvector 组件和 PostgreSQL 驱动，运行现有 Maven 测试；基础设施提供独立 Compose、SQL 和环境变量。
- [x] 2. 统一检索：新增 `retrieval/ProductRetrievalService`、查询/结果 DTO、文档映射器、pgvector 适配器；测试预算、售罄/冻结/下架过滤、RRF 去重、模型故障降级、候选扩展和无结果。
- [x] 3. 持久化及同步：稳定 UUID 文档 ID（pgvector M4 使用 UUID），文档摘要跳过重复 Embedding；索引写入串行化、读取最新商品、定期对账。测试重启复用、重复事件、并发旧事件、删除和写入失败。
- [x] 4. 商品事务写 PRODUCT_CHANGED Outbox，普通 Topic 投递与确认，消费写入成功才 ACK；测试事务回滚与 MQ payload，复用既有失败重试。
- [x] 5. 聊天与调试接口切换到同一服务，删除旧双实现；受控推荐输出校验商品 ID/证据，后端提供事实字段；管理入口鉴权和真实状态检查。
- [x] 6. 100 件合成商品与 50 条 dev/heldout 查询：独立评测库、幂等导入、约束检查、关键词基线和向量结果评分脚本；不修改现有支付订单数据。
- [x] 7. 运行编译、回归与可用基础设施集成验证，分别记录真实模型、固定测试向量和未运行部分；补充面试说明和 PLAN 阶段 B 状态。

## 验证命令与证据

后端：`mvn -Dmaven.repo.local=/private/tmp/tomatomall-m2 test`；定向测试位于 `src/test/java/com/example/tomatomall/retrieval/`。pgvector 集成测试通过环境变量显式开启，使用隔离数据库。固定测试 Embedding 只证明数据库与链路正确性，不能证明语义质量。真实模型评测需要有效 DashScope 配置。

实现结束后在本文补充实际结果、阻塞和配置方式。此前本机缺少 PostgreSQL 与 Docker，须如实记录安装/启动情况；不把配置文件交付当作真实服务验证通过。

## 执行结论

代码、打包、默认回归与真实PG/MQ/MySQL/HTTP验证已完成，见[验证记录](../测试/RAG验证记录.md)。已配置真实模型并完成首次、阶段一与阶段二评测，见[阶段二报告](../测试/2026-09-17-RAG阶段二修复验证.md)。工程与评测执行完成不等于业务质量全部达标，仍有误召回和生成稳定性边界。

## 下一步

按[购书场景重规划](购书场景-RAG精确查询与Agent职责重规划.md)推进B3-1、B3-2，再接C1只读导购；本阶段历史工程和质量报告保留。
