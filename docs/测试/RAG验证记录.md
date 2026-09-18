# RAG 验证记录（2026-09-16）

> 后续进展：2026-09-17 已完成真实模型首次对照，见 [新报告](2026-09-17-真实模型首次评测.md)。下文尚未接入的描述保留当时基线背景，当前状态以新报告为准。

## 1. 本次完成与未完成

完成统一检索、Spring AI PgVectorStore 持久化适配、普通商品事件同步、后台核对、引用与ID校验、100商品种子、50问题基线，以及真实数据库/消息/HTTP验证。现有8080后端已切换新版本，原有商城数据库和订单继续保留。

**未完成真实 Embedding/DeepSeek 推荐质量评测。** 本机两个模型密钥仍是占位配置，在线搜索会明确退回关键词。不能把固定向量的集成测试写成“语义召回提高”。阶段B的质量验收仍未完成。

## 2. 编译和测试证据

Java 17，Maven缓存 `/private/tmp/tomatomall-m2`。网络下载使用本机已有 Maven 仓库配置，不将企业镜像写进项目 pom。pgvector依赖版本与原Spring AI统一为1.0.0-M4。

| 验证 | 实际结果 | 证明范围 |
| --- | --- | --- |
| `mvn clean package`，随后权限修复后 `mvn package` | 最终默认76项：69通过、7项PG集成默认跳过，0失败/错误 | 原订单回归、新检索/聊天/预算/鉴权/Embedding边界/Outbox事务/消费逻辑 |
| `PgVectorPersistenceTest` 显式开启 | 7通过 | 真实PG持久化、新实例复用、重复跳过、失败回滚、模型隔离、缺失配置拒绝接管、并发writer |
| `RocketMqProductIT` 显式开启 | 1通过，约41秒 | 真实broker收到唯一一次发送，首次sync失败后真实重投成功；索引是故障double |
| `RagCatalogIT` 显式开启MySQL及PG参数 | 2通过 | 真实100商品与规格读取、MySQL/PG独立事务、全量索引和重复不重算、共享检索预算过滤、50条关键词预测 |
| Python fixtures工具测试 | 5通过 | 导入只新增、边界数据、预测完整性、违规结果/降级区分 |
| 最终JAR启动HTTP验证 | 通过 | 下述真实请求；另外发现并排除了PG默认自动配置误用MySQL的问题 |
| `git diff --check` | 通过 | 补丁空白检查 |

默认测试的7项跳过不是“没测”：另一次提供PG参数已真实执行。`*IT`不在默认Surefire命名范围，需要显式 `-Dtest=...`。没有本次重新宣称支付端到端或性能压测。

故障用例日志中的SQL溢出、模拟消息失败和断网异常是预期注入，判断结果看Surefire的Failures/Errors，而不是仅凭日志中出现ERROR。

## 3. 真实HTTP检查

在独立18080验证实例完成：

1. 创建提交 `role=admin` 的测试账号，实际保存为普通user。
2. 登录后检索Java项目，确认 `strategy=keyword`、`degraded=true`，返回5条且全部价格≤80、可售库存>0。
3. 数字query返回HTTP400。
4. 普通用户调用build返回HTTP403。
5. chat走同一检索，模型未配置时返回商品事实列表，不生成虚构价格或商品。
6. 排除 `PgVectorStoreAutoConfiguration` 后，启动日志仅创建自管的PG存储；MySQL仍是JPA数据源。

验证实例随后关闭。8080正式演示实例重启并用已有demo账号只读验证：查询Java返回1件“Java 编程入门”，关键词降级标记正确。

隔离验证使用 `tomatomall_fixture_rag_eval`。首次验证启动时复用了Redis DB0，库存预热短暂写入同ID缓存（30秒TTL）；已确认缓存过期，修正为独立Redis DB14，并在正式实例重启时按商城MySQL重新预热。未修改原商城商品或库存数据库记录。后续复制环境必须同时隔离数据库、Redis和MQ组。

## 4. 关键词基线（不是AI效果结论）

数据：100件合成商品，30开发问题/20保留问题，K=5。使用真实MySQL及与在线相同的ProductRetrievalService；没有调用Embedding。

| 指标 | 开发集 | 保留集 |
| --- | ---: | ---: |
| Hit@5（有相关标签的题目，至少命中一个） | 29/29 = 100% | 18/19 = 94.74% |
| 标注Precision@5 | 0.2138 | 0.1895 |
| 标注Recall@5 | 1.0000 | 0.9474 |
| 预算/类别/库存等硬约束违规问题数 | 0 | 0 |
| 出现标注禁止商品的问题数 | 11 | 2 |
| 查询验收通过（定义见下） | 19/30 = 63.33% | 17/20 = 85% |
| 未知商品ID问题数 | 0 | 0 |
| 无匹配问题正确返回空结果 | 1/1 | 1/1 |

每题标注集合通常很小，Precision@5按固定分母5计算；不能将0.2138解读为用户推荐满意度。禁止商品中含难度/用途不符的负例，表明关键词虽能召回目标，也会混入相近但不合适的商品。

原始排序预测：[开发集](结果/rag/keyword-dev.json)、[保留集](结果/rag/keyword-heldout.json)。保留集现已执行，未来若据此调参，应新增未使用题目作为独立验收集，不能继续宣称该集合完全未见。

评分可复现：

```sh
python3 infra/rag/fixtures/evaluate.py --predictions docs/测试/结果/rag/keyword-dev.json --split dev
python3 infra/rag/fixtures/evaluate.py --predictions docs/测试/结果/rag/keyword-heldout.json --split heldout
```

## 5. 本机新增环境

- PostgreSQL：Homebrew `postgresql@16` 16.15，程序 `/opt/homebrew/opt/postgresql@16/bin`。
- Homebrew pgvector 0.8.6二进制面向PG17/18；本机PG16实际从官方v0.8.0源码编译并安装兼容扩展。Compose也使用0.8.0-pg16。
- 独立PG数据目录 `/private/tmp/tomatomall-pgvector`，监听127.0.0.1:15432，日志 `/private/tmp/tomatomall-pgvector.log`。
- 本机隔离实例用户tomatomall、trust认证、无密码；仅作本机开发，不能照搬到共享网络。
- `tomatomall_vectors`：演示向量库，1024维；当前真实模型未配置，没有完成商品向量化。
- `tomatomall_rag_test`、`tomatomall_catalog_rag_test`：固定3维测试向量库，绝不能用于真实语义检索。
- MySQL `tomatomall_fixture_rag_eval`：100商品独立库，端口33077；演示商城仍为 `tomatomall_demo`。
- RocketMQ新增NORMAL topic `product-changed` / group `product-index-consumer`；独立重试测试资源后缀`-rag-test`。
- 8080新后端使用 `tmp/local-demo/application.properties`（Git忽略）；默认语义索引重建会因占位密钥失败，关键词搜索可用。
- 本轮没有安装Docker，Compose供迁移环境使用。Homebrew另初始化了默认PG目录，本次未启动默认集群，未注册开机服务。

启动独立PG：

```sh
/opt/homebrew/opt/postgresql@16/bin/pg_ctl -D /private/tmp/tomatomall-pgvector -l /private/tmp/tomatomall-pgvector.log -o '-h 127.0.0.1 -p 15432 -k /private/tmp' start
```

临时目录有被系统清理的风险，长期使用应迁移到持久目录或Compose命名卷；不能把此处开发目录当作备份。

## 6. 复现集成测试

在后端目录执行，使用独立测试数据库；PG测试会重建指定测试库中的索引表，名称必须以 `_rag_test` 结尾。

```sh
mvn -Dmaven.repo.local=/private/tmp/tomatomall-m2 -Dtest=PgVectorPersistenceTest -Drag.test.jdbc-url=jdbc:postgresql://127.0.0.1:15432/tomatomall_rag_test test
mvn -Dmaven.repo.local=/private/tmp/tomatomall-m2 -Dtest=RocketMqProductIT -Drag.test.mq-endpoints=localhost:18081 test
mvn -Dmaven.repo.local=/private/tmp/tomatomall-m2 -Dtest=RagCatalogIT '-Drag.test.mysql-url=jdbc:mysql://127.0.0.1:33077/tomatomall_fixture_rag_eval?useSSL=false&serverTimezone=Asia/Shanghai' -Drag.test.catalog-pg-url=jdbc:postgresql://127.0.0.1:15432/tomatomall_catalog_rag_test test
```

本机需要 Java17，并且沙箱内Mockito自附加可能失败，应在正常终端执行。日志位于 `/private/tmp/tomatomall-rag-*.log`，并非Git中的永久工件。

真实模型配置完成后，启动连接独立评测库的应用，使用管理员build并等status成功，再运行 `infra/rag/fixtures/collect_results.py` 收集 keyword/hybrid/vector 三种结果。工具保留degraded和未知ID，不能把故障降级结果当作向量成绩。

## 7. 指标复核与测试说服力（2026-09-17 补充）

本次只对仓库中已有的 50 条关键词预测重新评分，没有重新请求模型或运行集成环境。两份预测文件移至 `docs/测试/结果/rag/`，内容保持不变。

### 指标口径

- **Hit@5**：有相关标签的问题中，前 5 条至少包含一个相关商品的比例。无匹配问题另外统计，不混入分母。
- **Recall@5**：逐题计算“前 5 条命中的相关标签数 / 该题相关标签总数”，再对有相关标签的问题取平均。多标签场景下与 Hit 不同。
- **查询验收通过**：有匹配题须前 5 条覆盖全部相关标签；无匹配题须返回空；且整个返回列表不能有禁止商品、未知 ID 或硬约束违规。这是本项目的规则验收口径，不是用户满意度，也不保证所有未标注结果都合理。
- 两集合合并：有匹配题命中 47/48；查询验收通过 36/50；禁止商品污染 13/50；硬约束违规 0/50；无匹配正确 2/2。只有两条无匹配题，不能据此宣称拒答能力完善。
- 开发集未通过：q01、q03、q06、q07、q12、q14、q16、q17、q24、q25、q26；保留集未通过：q35、q43、q49。应逐条分析召回遗漏和错误候选，不能只看平均分。

下列脚本从仓库根目录复算 Hit 与查询验收数。Windows 可把代码保存到本地临时 `.py` 文件后执行（评分器主命令见第 4 节）：

```python
import json
import sys
from pathlib import Path
sys.path.insert(0, 'infra/rag/fixtures')
from evaluate import eligible, score, validate

def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))

queries = read('infra/rag/fixtures/queries.json')
products = validate(read('infra/rag/fixtures/products.json'), queries)
for split in ('dev', 'heldout'):
    chosen = [q for q in queries if q['split'] == split]
    predictions = read(f'docs/测试/结果/rag/keyword-{split}.json')
    print(split, score(products, chosen, predictions, 5))
    rows = {r['query_id']: r for r in predictions}
    hits = positive = passed = 0
    for q in chosen:
        keys = rows[q['query_id']]['fixture_keys']
        relevant = set(q['relevant_fixture_keys'])
        top = set(keys[:5])
        positive += bool(relevant)
        hits += bool(top & relevant)
        coverage = relevant <= top if relevant else not keys
        clean = not (set(keys) & set(q['forbidden_fixture_keys']))
        valid = all(k in products and eligible(products[k], q['constraints']) for k in keys)
        passed += coverage and clean and valid
    print(f'Hit@5={hits}/{positive}; query_pass={passed}/{len(chosen)}')
```

### 目前能够证明与尚不能证明的内容

| 维度 | 现有证据 | 边界 |
| --- | --- | --- |
| 工程正确性 | 真实数据库持久化、并发写入、事件重投、权限和降级测试 | 固定 3 维向量证明存储链路，不能证明语义能力；MQ 故障 double 也不等于全链路故障演练 |
| 检索质量 | 100 商品、50 题、固定标签、真实 MySQL 关键词预测 | 尚无真实 Embedding 的 vector/hybrid 对照成绩 |
| 业务过滤 | 预算、分类、库存、下架、全冻结边界 | collector 直接提交结构化约束，未验证完整自然语言理解 |
| 生成质量 | ID／引用校验和模型失败后的事实降级 | 没有真实模型推荐理由的独立人工评分 |
| 性能与稳定性 | 部分单次响应耗时及工程故障用例 | 没有负载模型、吞吐、P95/P99 或生产可用性证明 |

测试集**不全面**：只有 100 件合成图书，其中 science 占 48 件；商品、问题和标签由同一开发过程构造，可能有词汇重合与标注偏差，尚未经过独立人工复核。题目之间存在同主题变体，保留集已经执行并用于问题分析。真实用户语言、错别字、模糊需求、难度偏好、多轮对话、提示注入、长文档及更多无匹配问题仍不足。工程测试对库存变化的验证不能替代对这些问题的语义评测。

### 后续质量验收怎么做

1. **冻结对照条件**：记录代码 commit（未提交则记录补丁）、商品／问题文件 SHA-256、模型及版本、维度、文档规则版本、topK、阈值和运行时间。历史预测未完整保存这些元数据，目前只能保证离线分数可复算，不能声称完全复现当时运行环境。
2. **补独立标签与新题**：由未参与调参的人复核相关性，给前 5 条候选补分级相关标签；新增未参与调参的真实或脱敏用户问题，按预算、主题、难度、负例、无匹配等分层报告。先冻结标签，再比较方法。
3. **同条件跑三种策略**：keyword、真实 vector、hybrid 使用同一商品快照和查询；分别报告 Hit、Recall、分级标签下的 NDCG、禁止结果率、硬约束违规率、无匹配表现及降级率。不能排除失败题或把关键词降级算作向量成功。
4. **单独评估聊天链路**：只提交自然语言，检查约束提取、推荐商品、引用依据、价格事实和理由准确性；评审时隐藏策略名称，避免倾向新方案。
5. **保存失败案例与运行成本**：保留原始响应，记录延迟分布和模型调用成本。样本量较小时报告分子／分母，不仅给百分比；质量与性能门槛应在最终验收前确定，不能根据结果反向定标准。

面试时可表述为：“完成了可复现的检索评测管线和关键词基线，并发现关键词污染问题；真实向量方案的质量收益仍待独立对照验证。”当前不能表述为“RAG 准确率达到 95%”。

## 最新阶段二结果

已完成结构化过滤与候选修正，真实检索186次及生成10题的原始数据、指标和失败实验见[阶段二修复验证](2026-09-17-RAG阶段二修复验证.md)。此前章节保留其历史执行口径。
