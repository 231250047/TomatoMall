# RAG 无匹配与模型异常处理（阶段一）

## 1. 原问题与修正

原 ChatServiceImpl 将“合法空选品”“模型调用失败”“格式错误”都归成空集合，随后把全部候选填回。用户即使问没有对应商品的主题，模型已拒绝推荐，页面仍显示无关商品。

现在按结果分开处理：

| 情况 | 返回行为 |
| --- | --- |
| 检索结果为空 | 说明没有找到商品，不调用生成模型 |
| 模型合法返回 items=[] | 说明本次候选没有足够匹配商品，不回填候选 |
| 合法非空选品 | 校验候选 ID、原文引用，再读 MySQL 核对价格、库存、上下架 |
| 空正文、截断、HTTP／网络失败、非法 JSON、非法选品 | 提示推荐暂不可用，不展示未经相关性复核的候选 |

失败时保留普通商城搜索能力。此阶段没有实现独立的相关性复核器，因此不把“通过价格库存校验”误当成“符合语义需求”。无匹配文案限定在本次候选，避免断言整个商城一定没有。

现有字符串 API 与前端展示协议保持不变。没有实现查询改写、多轮历史理解或 Agent 工具路由，也没有修改向量阈值与 RRF。

## 2. 调用层诊断

DeepSeekServiceImpl 两个入口共用一条请求／解析路径，保持指定模型、地址、max_tokens=2000、temperature=0.7 和现有 5 秒连接／15 秒读取超时。

响应分类：SUCCESS、EMPTY_RESPONSE、EMPTY_CHOICES、EMPTY_CONTENT、TRUNCATED、UNSUPPORTED_FINISH、INVALID_RESPONSE、HTTP_ERROR、TIMEOUT、TRANSPORT_ERROR、NOT_CONFIGURED 等。非空正文但 finish_reason=length 也视为截断，不能把部分内容当成完整成功响应。空正文若同时结束于 length，分类为 EMPTY_CONTENT，并保留 finishReason=length。

日志仅保存：

- 本地 callId、请求和实际响应模型；
- outcome、HTTP 状态、finishReason；
- prompt/completion/total/reasoning tokens（缺失则 null，不伪造为零）；
- 是否有思考内容的布尔值、调用耗时。

不记录用户原文、候选正文、模型回答、思考正文、密钥和供应商错误响应正文；异常对外只带受控原因。远端模型／结束原因字段限制字符与长度，防止日志注入。该改动替代原有完整请求／回复和异常堆栈日志。

ChatServiceImpl 另记录 NO_MATCH、SELECTED、EMPTY_CONTENT、INVALID_JSON、INVALID_SELECTION 等选品结果。日志没有升级为监控告警平台，也没有增加隐式重试和额外付费请求。

## 3. 为什么本次没有直接调整思考模式？

新诊断在真实复测中记录到 reasoning tokens 较高，一条达到 1875，总 completion tokens 1937，接近 2000 上限；但本轮没有复现空正文，结束原因都是 stop。它提供了后续排查方向，不能据此倒推历史失败原因。

本次保持调用参数，避免同时改变结果处理、阈值和模型参数。后续若捕获 EMPTY_CONTENT + length 等证据，再单独对照非思考模式或更高输出额度，并衡量相关性、成本和延迟。

## 4. 代码和验证

- [ChatServiceImpl](../../tomatoMall-backend/src/main/java/com/example/tomatomall/service/serviceImpl/ChatServiceImpl.java)：三类业务结果与合法性校验。
- [DeepSeekServiceImpl](../../tomatoMall-backend/src/main/java/com/example/tomatomall/service/serviceImpl/DeepSeekServiceImpl.java)：响应分类和诊断。
- [GroundedChatTest](../../tomatoMall-backend/src/test/java/com/example/tomatomall/retrieval/GroundedChatTest.java)：空选品、错误响应、ID／引用与实时事实。
- [DeepSeekDiagnosticsTest](../../tomatoMall-backend/src/test/java/com/example/tomatomall/service/DeepSeekDiagnosticsTest.java)：空正文、截断、HTTP、超时、非法响应及日志隐私。
- [验证记录](../测试/2026-09-17-RAG阶段一修复验证.md)：回归和真实逐题结果。
