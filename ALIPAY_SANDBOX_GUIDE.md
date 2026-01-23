# 支付宝沙箱测试完整指南

## 📋 目录
1. [沙箱支付的两种模式](#沙箱支付的两种模式)
2. [方案对比](#方案对比)
3. [推荐方案：使用同步返回（无需 natapp）](#推荐方案使用同步返回无需-natapp)
4. [备选方案：使用内网穿透](#备选方案使用内网穿透)
5. [常见问题](#常见问题)

---

## 🎯 沙箱支付的两种模式

### 1️⃣ 异步通知（notify_url）

```
用户支付 → 支付宝 → 调用商户后端 notify_url → 更新订单
```

**特点**：
- ✅ 可靠性高（支付宝会重试多次）
- ✅ 不依赖用户操作
- ❌ **需要公网地址**（natapp/ngrok/云服务器）
- 🎯 用于生产环境

**流程**：
1. 用户在支付宝页面完成支付
2. 支付宝服务器主动调用你的 `notify_url`
3. 你的后端验证签名 → 更新订单状态 → 扣减库存
4. 返回 "success" 给支付宝

---

### 2️⃣ 同步返回（return_url）

```
用户支付 → 支付宝 → 跳转回 return_url → 前端/后端处理
```

**特点**：
- ✅ **无需公网地址**（localhost 即可）
- ✅ 用户体验好（即时跳转）
- ⚠️ 依赖用户不关闭页面
- 🎯 用于开发测试

**流程**：
1. 用户在支付宝页面完成支付
2. 支付宝跳转到你的 `return_url`（带订单号等参数）
3. 你的后端/前端处理支付成功逻辑
4. 显示支付结果页面

---

## 📊 方案对比

| 特性 | 异步通知（notify_url） | 同步返回（return_url） |
|------|----------------------|---------------------|
| **公网地址** | ✅ 必需 | ❌ 不需要 |
| **可靠性** | ⭐⭐⭐⭐⭐ 极高 | ⭐⭐⭐ 中等 |
| **用户体验** | ⭐⭐⭐ 一般 | ⭐⭐⭐⭐⭐ 优秀 |
| **实现难度** | ⭐⭐⭐⭐ 较难 | ⭐⭐ 简单 |
| **适用场景** | 生产环境 | 开发测试 |
| **需要工具** | natapp/ngrok | 无 |

---

## 🚀 推荐方案：使用同步返回（无需 natapp）

### 配置说明

你的 `application.yml` 中：

```yaml
alipay:
  notify-url: http://v86d9fd3.natappfree.cc/api/orders/notify  # 异步通知（可选）
  return-url: http://localhost:8080/api/orders/returnUrl       # 同步返回（已配置）
```

**✅ return-url 使用 localhost 即可，无需公网地址！**

### 测试流程

#### 1️⃣ 准备工作

**获取沙箱账号**：
- 访问：https://open.alipay.com/develop/sandbox/app
- 登录后查看买家账号信息：
  ```
  账号：xxxxx@sandbox.com
  登录密码：111111
  支付密码：111111
  余额：999999.00 元（虚拟金额）
  ```

#### 2️⃣ 创建订单

1. 启动后端和前端
2. 登录商城
3. 加购商品
4. 点击"结算" → 创建订单

#### 3️⃣ 支付测试

1. 点击"支付"按钮
2. 跳转到支付宝沙箱支付页面
3. 输入沙箱买家账号和密码（见步骤1）
4. 确认支付

#### 4️⃣ 验证结果

支付完成后，支付宝会自动跳转到：
```
http://localhost:8080/api/orders/returnUrl?out_trade_no=5&trade_no=xxx&total_amount=30.00
```

你的后端会：
1. 接收到订单号等参数
2. 更新订单状态为 SUCCESS
3. 扣减 MySQL 库存
4. 重定向到前端订单详情页

#### 5️⃣ 验证库存扣减

```sql
-- 查看订单状态
SELECT order_id, status, payment_method, total_amount 
FROM orders 
WHERE order_id = 5;

-- 查看库存变化
SELECT product_id, amount, frozen 
FROM stockpiles 
WHERE product_id = 51;
```

**预期结果**：
- 订单状态：`SUCCESS`
- 库存数量：`399`（从 400 减 1）
- Redis 库存：`399`
- MySQL 库存：`399`

---

## 🔧 备选方案：使用内网穿透

如果你想体验完整的生产环境流程（异步通知），可以使用内网穿透工具。

### 方案A：natapp（推荐）

1. **下载**：https://natapp.cn/
2. **注册并获取 authtoken**
3. **运行**：
   ```bash
   natapp -authtoken=你的token
   ```
4. **获取映射地址**（例如 `http://abc123.natappfree.cc`）
5. **更新配置**：
   ```yaml
   alipay:
     notify-url: http://abc123.natappfree.cc/api/orders/notify
   ```
6. **重启后端**

### 方案B：ngrok

```bash
ngrok http 8080
```

### 方案C：花生壳

免费版提供固定域名，适合长期开发。

---

## 🛠️ 测试工具：手动触发支付回调

如果不想真实支付，可以使用测试接口：

### 方法1：PowerShell 脚本

运行项目根目录的 `test_alipay_callback.ps1`：

```powershell
.\test_alipay_callback.ps1
```

### 方法2：Postman 测试

**接口**：`POST http://localhost:8080/api/orders/test-notify`

**参数**（form-data）：
```
out_trade_no: 5
trade_no: 2024012322001234567890
total_amount: 30.00
```

### 方法3：curl 命令

```bash
curl -X POST http://localhost:8080/api/orders/test-notify \
  -d "out_trade_no=5" \
  -d "trade_no=2024012322001234567890" \
  -d "total_amount=30.00"
```

---

## ❓ 常见问题

### Q1: 支付成功了，但订单状态还是 PENDING？

**原因**：
- notify_url 配置的是 natapp 地址，但 natapp 没有运行
- 支付宝无法访问你的后端

**解决**：
- 使用同步返回（return_url），无需 natapp
- 或者启动 natapp 并更新 notify_url

---

### Q2: Redis 库存扣减了，MySQL 没扣减？

**原因**：
- 下单时 Redis 已扣减（原子操作）
- 支付回调没执行，所以 MySQL 没同步

**解决**：
- 确保支付回调成功执行（检查后端日志）
- 或使用测试接口手动触发

---

### Q3: 重复支付显示 "TRADE_HAS_SUCCESS" 错误？

**原因**：
- 订单已经支付过了
- 支付宝不允许重复支付同一订单号

**解决**：
- 这是正常现象，说明支付已成功
- 查看 MySQL 确认订单状态是否为 SUCCESS
- 如果是 PENDING，说明回调没执行，手动触发即可

---

### Q4: 如何查看支付宝沙箱的交易记录？

登录支付宝沙箱账号：
- 网页版：https://openauth.alipay.com/oauth2/sandbox.htm
- 使用沙箱买家账号登录
- 查看交易记录

---

## 📝 总结

**推荐开发流程**：
1. ✅ **本地开发**：使用同步返回（return_url），无需 natapp
2. ✅ **快速测试**：使用测试接口 `/test-notify` 模拟回调
3. ✅ **生产环境**：使用异步通知（notify_url），部署到云服务器

**面试展示**：
- 说明了解两种回调机制的区别
- 强调生产环境应使用异步通知（更可靠）
- 本地测试用同步返回（更方便）
- 展示 Redis 原子操作防超卖的完整流程

---

## 🎯 下一步

1. 重启后端（应用新的 returnUrl 逻辑）
2. 创建新订单并支付
3. 观察支付完成后是否自动跳转并更新库存
4. 验证 Redis 和 MySQL 库存一致
