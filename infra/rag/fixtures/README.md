# RAG 测试数据与工具

这是专用于商品检索评测的目录，不是商城生产数据，也不会被应用自动导入。包含 100 件虚构商品和 50 条预先标注的问题；同一数据可比较关键词、向量、混合检索。

| 文件 | 用途 |
| --- | --- |
| products.json / queries.json | 商品种子／问题、约束、相关与禁止商品标签 |
| build_dataset.py / build_queries.py | 重建固定数据；平时直接使用 JSON 即可 |
| import_fixtures.py | 导入隔离 MySQL，输出稳定 key 与实际商品 ID 的映射；重复执行不重置库存 |
| collect_results.py | 调用运行中的检索接口，保存预测及原始响应 |
| evaluate.py | 校验数据、对预测评分 |
| test_tools.py | 离线工具边界测试 |

## 先复算已有结果（不需要 MySQL、模型或密钥）

在仓库根目录执行，Python 3 无额外 pip 依赖；macOS/Linux 可使用 `python3`，Windows 使用 `python`：

```sh
python infra/rag/fixtures/evaluate.py
python infra/rag/fixtures/evaluate.py --predictions docs/测试/结果/rag/keyword-dev.json --split dev --k 5
python infra/rag/fixtures/evaluate.py --predictions docs/测试/结果/rag/keyword-heldout.json --split heldout --k 5
python infra/rag/fixtures/test_tools.py
```

## 测试自己的检索服务

顺序：初始化隔离环境 → 导入与保存 ID 映射 → 启动评测应用 → 向量模式构建索引 → 登录设置 RAG_EVAL_TOKEN → 采集预测 → 评分。

完整准备步骤和命令见 [RAG 测试数据与评测](../../../docs/测试/RAG测试数据与评测.md)。MySQL 数据库名称必须以 `_rag_eval` 结尾，并与商城交易数据隔离；PG、Redis、MQ 资源也要隔离。导入需 mysql CLI；采集需已启动应用及 token；真实向量检索另外需要有效 Embedding 配置和就绪索引。

已有分数与不足见 [RAG 验证记录](../../../docs/测试/RAG验证记录.md)：当前是关键词基线；固定向量的存储集成测试不代表真实模型的语义效果。

## 阶段二新增验收题

`stage2-acceptance.json` 是冻结的 12 条开发者编写新题。采集时添加 `--queries infra/rag/fixtures/stage2-acceptance.json --split all`，会传递 level/excludedTopics。离线复算：

```sh
python infra/rag/fixtures/evaluate.py --queries infra/rag/fixtures/stage2-acceptance.json --split all --predictions docs/测试/结果/rag/2026-09-17-stage2/hybrid-acceptance.json
```

真实模型数据和局限见[阶段二报告](../../../docs/测试/2026-09-17-RAG阶段二修复验证.md)，上述历史关键词基线不是最新全部结果。

## 单轮购书Agent原话评测

`shopping-acceptance.json`冻结16条用户原话。`collect_shopping_results.py`仅向推荐接口传query，不发送人工标注的预算等条件：

```sh
python infra/rag/fixtures/collect_shopping_results.py --mapping <隔离库ID映射.json> --output <评测结果.json>
```

使用RAG_EVAL_TOKEN登录令牌；默认127.0.0.1:18080，可传--base-url。可选--log-path读取本次实例安全诊断以核对路由，不读取或保存密钥。指标中的目标命中、预算违规和路由正确分别记录；语义无完备标签题不计算虚假的推荐准确率。16题不替代后续40题计划。
