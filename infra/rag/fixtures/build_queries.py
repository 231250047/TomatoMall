#!/usr/bin/env python3
"""Curated relevance labels with human-readable reasons; not search-generated labels."""
import json
from pathlib import Path
# query | relevant numeric fixture keys | explicit negative keys | human rationale | max price | category
ROWS = '''Java完全没学过，先看哪本基础书？|1|3,5|001明确无前置要求并讲变量循环；003与005是源码级内容||science
学完Java基础，想做商城项目，单本不超过80元|2|41,42,43,44|002是项目实践且79.90元；041超预算，042售罄，043下架，044库存全冻结|80|science
想看Java虚拟机垃圾回收和字节码源码|3|1,2|003明确覆盖垃圾回收、字节码及源码||science
线程池执行的任务如何安全取消？|4|1|004描述明确包含线程池、线程安全与任务取消||science
想看HashMap为什么扩容|5||005学习目标明确为HashMap扩容||science
不太会编程，想用Python清洗表格和画统计图|6|7|006无需前置，覆盖清洗统计可视化；007侧重接口||science
MySQL并发读写为什么会读到不同版本？|8|9|008覆盖MVCC与事务隔离；009侧重索引||science
找执行计划和联合索引调优的书，每本最多80元|9,45|46|009价格80.00，045价格79.99，046价格80.01超过预算|80|science
完全不会SQL，想学表连接与分组聚合|10|12|010无前置且覆盖连接聚合；012是高级理论||science
想用PostgreSQL存储文本向量|11|12|011明确包含pgvector与语义检索||science
数据库更新后缓存还是旧值，想学怎么处理|13|14,64|013覆盖旁路缓存与一致性；064是虚构小说||science
想学Redis常用字符串哈希列表命令|14|16|014是基础命令入门；016要求C语言||science
如何用Lua原子扣减秒杀库存并补偿失败？|15|82|015覆盖Lua预占与补偿；082是经营库存管理||science
想读Redis内存编码实现源码|16|14|016学习目标为内存分配和编码，要求C语言||science
消费者同一条消息执行两遍怎么避免？|17,47|63,80|017与047覆盖幂等消费；063小说、080管理沟通均非消息队列||science
订单半小时没付款如何通过消息自动关闭？|18|19|018覆盖订单超时与支付竞争；019是流处理||science
刚接触MQ，生产者和消费者是什么？|20|19|020无前置且覆盖生产消费与发布订阅||science
想写Vue商品列表和购物车|21|23|021明确覆盖商品卡片与购物车交互||science
TypeScript泛型和联合类型怎么组织？|23|22|023学习目标包含泛型与联合类型||science
网页为什么布局和绘制很慢？|24|21|024覆盖渲染流程与性能分析||science
怎么做有商品证据和离线评测的RAG推荐？|25|29,94|025商品检索与证据引用；029只有提示词，094讲伦理||science
不懂文本向量，想学相似度与语义搜索入门|26|28|026是向量搜索入门；028需要微积分并侧重数学||science
Agent执行工具前怎么校验参数和确认权限？|27|94|027覆盖工具权限与用户确认；094不是API开发||science
怎么核验支付回调并处理重复通知？|39|18|039覆盖验签、金额检查及重复通知幂等||science
写完数据库怎样保证消息最终发出去？|40|20|040明确覆盖Outbox与最终一致性；020仅基础概念||science
想读诗歌意象和节奏赏析，50元以内|65|66|065是阅读赏析且39元；066侧重创作|50|literature
手机拍照不知道怎么构图|73|72|073讲手机摄影光线与构图；072是素描||art
想看电商转化率和复购怎么分析|78|82|078覆盖转化复购经营分析；082侧重补货||management
想了解港口商路和海上贸易制度|90|86|090明确讲港口商路制度||history
商品中有量子芯片制造工艺专著吗？|||所有商品均未覆盖芯片制造工艺，应返回无匹配||science
预算80元以内，Java项目实战想买一本有现货的|2|41,42,43,44|002满足项目与预算；其余版本分别超价售罄下架冻结|80|science
执行计划优化的书严格低于80元|45|9,46|045为79.99元；009为80元不满足严格低于|79.99|science
我只有79.99元，想学联合索引和慢查询|45|9,46|045恰好满足预算；009与046超价|79.99|science
多次收到扣款通知，如何防止订单被重复更新？|39|63|039支付回调幂等；063只是含消息关键词的小说||science
服务保存业务记录后崩溃，如何补发未投递事件？|40|20|040涵盖Outbox与补偿；020不涉及该进阶机制||science
召回服务调用模型超时，能否退回关键词查商品？|48|29|048明确包含模型超时与关键词降级||science
推荐回答花钱多少、等待多久，如何做指标和评测？|30|25|030明确覆盖成本、延迟与离线评测||science
查到同义句不一定有相同字词，怎么学向量搜索？|26|22|026覆盖语义检索与向量相似度||science
怎么让应用打包进容器并把多个服务联网？|35|34|035覆盖容器网络与编排；034是命令起步||science
线上请求报错，想用日志指标和调用链排查|36|35|036明确覆盖追踪、指标和故障定位||science
不了解证书链和TLS握手，读什么？|32|31|032明确包含证书链与TLS；031是HTTP入门||science
树链表和哈希表的图解入门书，不超过50元|37|38|037价格49且为图解入门；038高级证明|50|science
面试时讲不清自己的项目，想练表达和复盘|54|59|054是项目沟通表达；059是技术知识复习||education
团队开会如何传递信息，想要管理类书|80|17,47|080为管理沟通；017与047是技术消息队列||management
找一本标题叫缓存的记忆的小说|64|13|064书名精确匹配且为文学，013是技术缓存||literature
对AI伦理公平和责任感兴趣，不想学接口开发|94|27|094涵盖公平责任社会影响；027侧重工具开发||philosophy
想学命题论证，识别日常逻辑谬误|91|93|091明确覆盖命题论证与谬误||philosophy
怎样辨别健康资讯是否有可靠证据？|100|96|100覆盖信息来源与证据辨识||health
想记录作息改善睡眠习惯，40元以内|98|97|098是睡眠习惯且39元；097是饮食记录|40|health
20元以下有没有可以买到的书？|||测试集最低价格25元，预算内没有商品|20|'''
queries=[]
key=lambda n: f'rag-v1-{int(n):03}'
for i,row in enumerate(ROWS.splitlines(),1):
    query, relevant, forbidden, rationale, maximum, category=row.split('|')
    constraints={'in_stock':True}
    if maximum: constraints['max_price']=maximum
    if category: constraints['category']=category
    queries.append(dict(query_id=f'q{i:02}',split='dev' if i<=30 else 'heldout',query=query,
        constraints=constraints, relevant_fixture_keys=[key(x) for x in relevant.split(',') if x],
        forbidden_fixture_keys=[key(x) for x in forbidden.split(',') if x], rationale=rationale,
        expected_behavior='recommend' if relevant else 'no_match'))
(Path(__file__).resolve().parent/'queries.json').write_text(json.dumps(queries,ensure_ascii=False,indent=2)+'\n')
print(f'Wrote {len(queries)} queries')
