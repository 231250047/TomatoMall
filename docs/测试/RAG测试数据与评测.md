# RAG 测试数据与评测

通用指标、项目分数与真实模型准备见 [RAG 评测指标与方法](RAG评测指标与方法.md)。本文聚焦工具使用。

## 1. 目标和证据边界

为 Spring AI + pgvector 商品检索提供可重复的数据基础。种子是 **100 件虚构商品**，不对应真实出版物；每件商品标题、简介和详情均注明合成测试。数据库中的商品 ID 不固定，评测通过稳定的 `fixture_key` 和映射表关联。

本文件中的“预期相关商品”是依据商品内容预先编写的规则标签（由项目开发过程构造，尚未经独立人工复核），**不是已经运行出的检索结果**。离线数据校验通过，不代表实际 Embedding 的语义召回率达标。只有配置真实模型并执行统一检索接口，才能报告关键词与向量检索的效果差异。

## 2. 文件清单

相对仓库根目录：

| 文件 | 用途 |
| --- | --- |
| `infra/rag/fixtures/products.json` | 可导入的 100 件商品及规格、库存 |
| `infra/rag/fixtures/queries.json` | 50 条固定问题、结构化约束、相关/禁止结果与依据 |
| `infra/rag/fixtures/build_dataset.py` | 从固定的合成条目重建商品 JSON，无网络或模型依赖 |
| `infra/rag/fixtures/build_queries.py` | 重建固定问题与标签，无检索结果参与 |
| `infra/rag/fixtures/import_fixtures.py` | Python 标准库 + MySQL CLI 的只新增导入器 |
| `infra/rag/fixtures/collect_results.py` | 调用实际检索接口，保存排序预测及原始响应 |
| `infra/rag/fixtures/evaluate.py` | 数据约束校验、实际检索预测结果评分 |
| `infra/rag/fixtures/test_tools.py` | 评分器与导入生成器的离线边界测试 |

这些合成文件可以提交 Git。模型密钥、数据库口令、支付信息、实际数据库文件和向量存储目录不应提交。向量可使用合成商品和指定模型重新生成，生成时记录模型名称、维度、文本规则版本与代码提交。

## 3. 数据设计

类别分布：science 48、education 12、literature 10、art 6、management 8、history 6、philosophy 5、health 5。

内容覆盖 Java、MySQL、Redis、消息队列、前端、RAG、Agent、网络、部署等；非技术书覆盖其他全部合法类目。规格包含主题、适用难度、学习目标、前置知识。简介不超过 255 字、详情不超过 500 字，遵守现有实体约束。

### 特别设计的边界

| fixture_key | 场景 | 预期 |
| --- | --- | --- |
| rag-v1-002 | Java 商城实践 79.90 元、有可售库存 | 预算 80 元时可推荐 |
| rag-v1-041 | 相近内容 80.01 元 | 预算 80 元时排除 |
| rag-v1-042 | 相近内容，但 amount=0 | 排除售罄商品 |
| rag-v1-043 | 相近内容，但 status=unavailable | 排除下架商品 |
| rag-v1-044 | amount=20、frozen=20 | 可售为零，排除；不是 amount>0 就有货 |
| rag-v1-009 / 045 / 046 | 相近索引调优内容，价格 80.00 / 79.99 / 80.01 | 验证小数精度、包含等于和严格小于的边界 |
| rag-v1-063 / 064 / 069 | 消息、缓存、数据库关键词出现在小说标题 | 不应当作为相应技术书推荐 |
| rag-v1-080 / 082 / 094 | 管理沟通、经营库存、AI 伦理 | 区分业务含义，不能仅凭相同关键词推荐技术书 |

Frozen=20 的条目是孤立库存边界样本，并未创建一笔冻结库存订单，因此不用于订单健康检查和支付测试。

## 4. 独立数据库与导入

### 准备数据库

使用单独的 MySQL 数据库，例如 `tomatomall_rag_eval`，**不要对当前 `tomatomall_demo` 执行种子导入**。导入器强制要求名称以 `_rag_eval` 结尾，且数据库必须为空商品库或完全由该套 fixture 管理的商品库。

数据库需先由当前版本应用/迁移初始化结构；导入器不会隐式创建或修改业务表。也可使用 `mysqldump --no-data` 从当前应用结构导出后导入新数据库，不能把包含旧商品、订单的历史全量 SQL 当空库结构导入。MySQL 业务数据库、PostgreSQL 向量存储、Redis DB／实例和 MQ topic／消费组都应与原商城隔离，应用运行评测时明确指向这两个评测存储。

初次导入需要 `products`、`stockpiles`、`specifications` 三表及当前实体列。`seller_id` 可为空；需要在前端展示店铺时，先在独立库建立演示卖家，再显式传入 `--seller-id`。

### 导入命令

从仓库根目录执行；按实际情况替换 MySQL 路径和端口。下列多行命令采用 POSIX shell，Windows PowerShell 请合并成一行，`python3` 可换为 `python`。先运行 `python -c "from pathlib import Path; Path('tmp/rag').mkdir(parents=True, exist_ok=True)"` 创建输出目录：

```bash
python3 infra/rag/fixtures/evaluate.py

python3 infra/rag/fixtures/import_fixtures.py \
  --mysql mysql \
  --host 127.0.0.1 --port 3306 --user root \
  --database tomatomall_rag_eval \
  --mapping-out tmp/rag/fixture-map.json
```

需要密码时，使用权限为 600、位于 Git 忽略目录的 MySQL option 文件，并传 `--defaults-extra-file /absolute/private/client.cnf`。不要把密码放进命令行或文档。脚本通过参数数组执行 CLI，不拼接 shell 命令。

仅查看 SQL，不连接数据库：

```bash
python3 infra/rag/fixtures/import_fixtures.py \
  --database tomatomall_rag_eval --sql-only > /tmp/tomatomall-rag-fixtures.sql
```

`--sql-only` 只供审阅；手工执行会绕过 Python 的空库检查，应使用导入器完成正式导入。请单进程执行导入，不同时启动多个导入器。

### 重复导入语义

额外表 `rag_fixture_products` 保存 `fixture_key → product_id` 和种子内容 SHA-256：

- 仅插入未出现过的 fixture，每个商品、库存、规格和映射在同一个数据事务中写入。
- 重复导入不会更新原商品价格、库存、状态或规格，不会删除交易记录。
- 修改过的库存不会被重置成种子库存，避免覆盖演示交易。
- 如果 fixture 定义变了但沿用相同 key，导入器拒绝，需使用新版本或新评测库。
- 若评测库已经发生交易或修改商品，静态评分器不会读取这些实时变化。正式质量评测应在新初始化且冻结写入的评测库进行，不能把“导入不覆盖”误认为“数据会自动恢复”。

数据直接通过 SQL 导入，不会产生应用 Outbox 事件。**导入完成后必须调用统一索引重建入口**，并等待索引就绪。不得在共享向量表中执行清空式重建。

## 5. 50 条问题如何使用

- `q01`—`q30`：开发集，用于观察召回与调整策略。
- `q31`—`q50`：保留验证集，最初设计为在确定策略后运行；现已执行并用于问题分析，后续调参需另建未使用验收集。两个集合有少量同主题边界变体，因此这是项目验收集，不宣称统计独立的公开学术基准。
- 每条包含 `relevant_fixture_keys`、`forbidden_fixture_keys`、可理解的 `rationale`。
- `constraints` 保存人工明确的预算、分类和可售要求。
- 无相关商品的样本要求返回无匹配，不强行凑够数量。

先直接把 `query` 与人工 `constraints` 传给统一检索服务，单独评估检索和硬条件过滤。再仅输入自然语言测试聊天链路，单独记录约束提取是否正确。否则无法区分“模型误解预算”和“数据库过滤出错”。例如“严格低于 80 元”在金额精度为两位小数的当前项目中标记为最多 79.99 元。

同样的查询、限制、topK、商品快照和代码版本分别运行关键词、向量、混合检索。关键词模式应确实关闭向量召回；模型故障的降级结果需标记，不能计成向量检索成功。

## 6. 收集结果并评分

调用实际检索接口得到商品 ID 后，使用 `--mapping-out` 导出的映射转换成 fixture key。保留原始响应作为证据；不要把无法映射的 ID 丢弃，应保留成未知 key，使评分器报告问题。

预测文件为 JSON 数组，每个查询必须且只能有一条：

```json
[
  {
    "query_id": "q01",
    "fixture_keys": ["rag-v1-001"],
    "degraded": false
  }
]
```

上面只是格式示例，不代表已经完成检索。完整 dev 文件须包含 30 条，heldout 文件须包含 20 条。采集和评分的 `--split` 必须一致；包含 50 条的 all 文件不能直接用 dev 参数评分。

```bash
python3 infra/rag/fixtures/evaluate.py \
  --predictions /tmp/rag-keyword-dev.json --split dev --k 5

python3 infra/rag/fixtures/evaluate.py \
  --predictions /tmp/rag-hybrid-heldout.json --split heldout --k 5
```

输出包含：

- `labeled_precision_at_k`：前 k 个结果命中预先标注集合的数量 / k；返回不足 k 个时分母仍为 k。
- `labeled_recall_at_k`：前 k 个命中的标签数量 / 该问题标签总数。
- 硬条件违规的查询数，包括未知商品、超预算、分类不符、下架、售罄、全冻结库存。
- 明确禁止结果出现的查询数、未知 ID 查询数、无匹配问题正确数、降级查询数。

标签针对明确意图选取，并非穷尽所有“可能有帮助”的商品，所以指标命名为 labeled，不能将低于 1 的 Precision 直接等同业务错误。对于标签外但合理的结果，需要先人工复核，冻结新标签版本后再重跑所有方法，不能只为新方案临时修改答案。

评分器校验最终返回列表中的所有硬条件，而不仅是前 k 条。应用请求是否真的发出、延迟、模型调用次数、真实价格来源、推荐理由是否有证据，需另外从原始响应/日志和人工审阅记录，不能由本离线评分器证明。

## 7. 验证记录

当前已执行：

- 生成 100 件商品和 50 条问题。
- 离线字段长度、类别数量、库存约束、标签存在性、相关商品满足硬条件、开发/保留集划分检查。
- 工具边界测试：缺少预测、重复预测、重复商品、未知 ID、全库存冻结、超预算、下架和无匹配。

2026-09-16 真实 MySQL 验证（本机 33077，独立 `tomatomall_fixture_rag_eval`）：

- 从当前商城复制三张表的结构；没有复制商城商品、订单或用户数据。
- 首次导入成功；重复导入后数量仍为：商品 100、库存 100、规格 400、映射 100。
- 在隔离库将 `rag-v1-001` 库存改为 amount=19、frozen=2 后再次导入，仍为 19/2，验证不会覆盖库存。
- 验证后已明确恢复该测试条目为 20/0；其他边界条目保持种子定义。
- 导入器对 `--database tomatomall_demo --sql-only` 拒绝执行，退出码 2。
- `python3 infra/rag/fixtures/test_tools.py`：5 项通过。
- 当次 ID 映射输出到本机 `/tmp/tomatomall-rag-fixture-map.json`；该运行产物未提交 Git。

上述是最初导入实验时的结构；后续完整应用 HTTP 验证已补齐应用表。重新搭建环境时仍需按当前版本初始化完整结构，并配置独立 PostgreSQL、Redis 和 MQ 资源，不能仅凭三张商品表启动完整商城。

真实模型语义效果与 pgvector 集成结果由本阶段整体实现验证记录补充；本文件不预填任何召回率改善结论。

## 8. 面试时可以解释什么

1. 为什么只有几件商品不能证明 RAG 有效：没有相近候选和负例，随机命中也可能看起来正确。
2. 为什么要拆开语义指标与业务违规：内容很相关的商品也可能超预算或无库存。
3. 为什么保留稳定 key：MySQL 自增 ID 在不同电脑和数据库中会变化。
4. 为什么重复导入不重置库存：种子初始化不应该覆盖交易状态；验收用新的隔离环境。
5. 为什么模拟向量测试不能代表真实效果：它验证调用契约与异常处理，无法验证实际 Embedding 的语义空间。
6. 为什么评测不能只看模型自己的评分：固定标签、明确约束、真实数据库事实与人工复核共同提供可追溯依据。

## 实际查询收集工具

新增 `collect_results.py`：从已登录的检索API采集结果，token仅通过本机环境变量RAG_EVAL_TOKEN提供，不写进命令参数或仓库。请指向连接评测MySQL库及独立向量库的应用，不要拿演示库商品ID套用评测映射。

```sh
python3 infra/rag/fixtures/collect_results.py --base-url http://127.0.0.1:18080 --mapping tmp/rag/fixture-map.json --split dev --mode hybrid --output /tmp/rag-hybrid-dev.json
python3 infra/rag/fixtures/evaluate.py --predictions /tmp/rag-hybrid-dev.json --split dev
```

同目录生成 `.raw.json` 原始响应。未知ID和降级结果保留，不能静默剔除。实际关键词基线结果见[验证记录](RAG验证记录.md)。

## 9. 一次完整评测的顺序

1. 校验 fixtures，初始化隔离库并导入，保存 ID 映射；冻结商品写入，保证评分标签与数据库事实一致。
2. 启动指向隔离 MySQL、PG、Redis、MQ 的应用。18080 只是示例端口，不会由脚本自动启动服务。
3. 向量／混合模式需要有效 Embedding 配置，以管理员调用 `POST /api/test/vectorstore/build`，用 `GET /api/test/vectorstore/status` 确认完成及索引可用；纯关键词评测不依赖向量就绪。
4. 登录该评测库账号获取 token。macOS/Linux 在终端设置 `export RAG_EVAL_TOKEN='实际token'`，PowerShell 使用 `$env:RAG_EVAL_TOKEN='实际token'`；不提交 token。
5. 对同一份数据分别采集 keyword、vector、hybrid，每种模式分别执行 dev 和 heldout；向量模式出现 degraded 时记录失败／降级，不能计作真实向量成绩。
6. 执行评分，保留预测和 `.raw.json`。整理失败问题、延迟、模型版本和数据版本；自然语言约束提取与最终推荐文案另做端到端验收。

注意：collector 会把 queries 中的结构化 constraints 一起交给接口，所以它验证的是检索与过滤，不能证明聊天模型正确理解预算、难度或多轮意图。当前数据质量边界与具体分数见 [RAG 验证记录](RAG验证记录.md)。
